import logging as log

from hashlib import md5
from time import time
from itertools import ifilter
import base64

from gi.repository import GLib, Wnck


def now():
    return long(time())


class TaskIconCache(object):
    """Manages the cache of icons associated to tasks."""

    def __init__(self):
        self.cache = {}
        self.refcounts = {}

    @property
    def size(self):
        return len(self.cache)

    def encode_pixels(self, pixels):
        return base64.standard_b64encode(pixels.encode('zlib_codec'))

    def store(self, pxbuf):
        """Caches an Gtk pixel buffer object."""

        pixels = pxbuf.get_pixels()

        hasher = md5()
        hasher.update(pixels)
        icon_id = hasher.hexdigest()

        if icon_id in self.cache:
            self.refcounts[icon_id] += 1
        else:
            self.cache[icon_id] = {
                'id': icon_id,
                'width': pxbuf.get_width(),
                'height': pxbuf.get_height(),
                'pixels': self.encode_pixels(pixels),
            }
            self.refcounts[icon_id] = 1

        return icon_id

    def deref(self, icon_id):
        if icon_id in self.cache:
            self.refcounts[icon_id] -= 1
            if self.refcounts[icon_id] == 0:
                del self.cache[icon_id]

    def fetch(self, icon_id):
        return self.cache.get(icon_id, {})

    def collect_garbage(self):
        # TODO: Not used yet. Concerns about clients possibly fetching orphaned icon IDs.
        for icon_id, refcount in self.refcounts.viewitems():
            if refcount <= 0:
                del self.cache[icon_id]
                del self.refcounts[icon_id]


class Task(object):
    """Internal representation of a Wnck window tracked by the screen manager."""

    def __init__(self, window, icon_cache):
        self.window = window
        self.icon_cache = icon_cache

        self.is_open = True
        self.last_update_ts = now()
        self.connected_handler_ids = set()
        self.icon_id = icon_cache.store(self.window.get_icon())

        self._refresh_properties()

    def __str__(self):
        return 'Window #%d ("%s")' % (self.window_id, self.title)

    def connect_signal(self, signal, callback):
        assert self.window is not None
        """Connect signal handlers for this window."""
        handler_id = self.window.connect(signal, callback)
        if handler_id > 0:
            self.connected_handler_ids.add(handler_id)

    def _bump_update_time(self, ts=None):
        self.last_update_ts = ts or now()

    def replace_window(self, window, update_ts=None):
        assert self.window is not None and window.get_xid() == self.window_id
        self.window = window
        self._refresh_properties()
        self._bump_update_time()

    def _refresh_properties(self):
        self.task_id = str(self.window.get_xid())
        self.window_id = self.window.get_xid()
        self.app_name = self.window.get_application().get_name()
        self.title = self.window.get_name()

    def focus(self):
        if self.window and self.is_open:
            self.window.activate(now())

    def maximize(self):
        if self.window:
            self.focus()
            self.window.maximize()

    def unmaximize(self):
        if self.window:
            self.focus()
            self.window.unmaximize()

    def close(self):
        self.is_open = False
        self._bump_update_time()

        # Disconnect all signal handlers
        for handler_id in self.connected_handler_ids:
            self.window.disconnect(handler_id)
        self.connected_handler_ids = set()
        self.icon_cache.deref(self.icon_id)
        self.window = None

    def to_json(self):
        return {
            'id': self.task_id,
            'app_name': self.app_name,
            'title': self.title,
            'icon_id': self.icon_id,
            'is_open': self.is_open,
            'last_update_ts': self.last_update_ts,
        }



class ScreenManager(object):
    def __init__(self, screen):
        self.screen = screen
        self.initialized = False
        self.icon_cache = TaskIconCache()
        self.tasks = {}

    def _ensure_initialized(self):
        if not self.initialized:
            raise Exception('ScreenManager is not initialized!')

    def _is_eligible_window(self, window):
        return window.get_window_type() == Wnck.WindowType.NORMAL

    def _on_window_name_changed(self, window, *args):
        window_id = window.get_xid()
        log.debug('Received "name-changed" for window %s (%s)', window_id, window.get_name())
        if window_id in self.tasks:
            task = self.tasks[window_id]
            task.replace_window(window)

    def _on_window_open(self, screen, window):
        if not self._is_eligible_window(window):
            return

        window_id = window.get_xid()
        log.debug('Window %d opened.', window_id)

        new_task = Task(window, self.icon_cache)
        new_task.connect_signal('name-changed', self._on_window_name_changed)
        self.tasks[window_id] = new_task

    def _on_window_close(self, screen, window):
        if not self._is_eligible_window(window):
            return

        window_id = window.get_xid()
        log.debug('Window %d closed.', window_id)

        if window_id in self.tasks:
            task = self.tasks[window_id]
            task.close()

    def initialize(self):
        """Should be called before use."""
        if self.initialized:
            return

        self.screen.force_update()
        for window in ifilter(self._is_eligible_window, self.screen.get_windows()):
            task = Task(window, self.icon_cache)
            self.tasks[window.get_xid()] = task

        log.info('ScreenManager initialized with %d tasks and %d icons.',
            len(self.tasks), self.icon_cache.size)
    
        self.screen.connect('window-opened', self._on_window_open)
        self.screen.connect('window-closed', self._on_window_close)
        self.initialized = True

    def list_tasks(self):
        self._ensure_initialized()
        return map(lambda task: task.to_json(), self.tasks.values())

    def get_icon(self, icon_id):
        self._ensure_initialized()
        return self.icon_cache.fetch(icon_id)

    def _idle_task_action(self, task_id, action_name):
        self._ensure_initialized()
        window_id = long(task_id)
        if window_id in self.tasks:
            task = self.tasks[window_id]
            action = getattr(task, action_name)
            def impl():
                action()

            GLib.idle_add(impl)
            return True

        return False

    def activate_task(self, task_id):
        return self._idle_task_action(task_id, 'focus')

    def maximize_task(self, task_id):
        return self._idle_task_action(task_id, 'maximize')

    def unmaximize_task(self, task_id):
        return self._idle_task_action(task_id, 'unmaximize')

