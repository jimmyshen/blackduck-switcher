import logging as log

from hashlib import md5
from time import time
from itertools import ifilter
import base64

from gi.repository import GLib, Wnck


class ScreenManager(object):
    def __init__(self, screen):
        self.screen = screen
        self.initialized = False

        self.tasks = {}
        self.icons = {}
        self.windows_by_task_id = {}

    def _encode_pixels(self, pixels):
        return base64.standard_b64encode(pixels.encode('zlib_codec'))

    def _cache_icon(self, icon):
        pixels = icon.get_pixels()

        hasher = md5()
        hasher.update(pixels)
        icon_id = hasher.hexdigest()

        if icon_id not in self.icons:
            self.icons[icon_id] = {
                'id': icon_id,
                'width': icon.get_width(),
                'height': icon.get_height(),
                'pixels': self._encode_pixels(pixels),
            }

        return icon_id

    def _window_to_task(self, window, last_update_ts):
        icon_id = self._cache_icon(window.get_icon())

        return {
            'id': str(window.get_xid()),
            'app_name': window.get_application().get_name(),
            'title': window.get_name(),
            'icon_id': icon_id,
            'is_open': True,
            'last_update_ts': last_update_ts,
        }

    def _is_eligible_window(self, window):
        return window.get_window_type() == Wnck.WindowType.NORMAL

    def _on_window_open(self, screen, window):
        if not self._is_eligible_window(window):
            return

        log.debug('Window %d opened.', window.get_xid())

        task = self._window_to_task(window, long(time()))
        self.tasks[task['id']] = task
        self.windows_by_task_id[task['id']] = window

    def _on_window_close(self, screen, window):
        if not self._is_eligible_window(window):
            return

        log.debug('Window %d closed.', window.get_xid())

        task_id = str(window.get_xid())
        if task_id in self.tasks:
            task = self.tasks[task_id]
            task['is_open'] = False
            task['last_update_ts'] = long(time())

        if task_id in self.windows_by_task_id:
            del self.windows_by_task_id[task_id]

    def _ensure_initialized(self):
        if not self.initialized:
            raise Exception('ScreenManager is not initialized!')

    def initialize(self):
        """Should be called before use."""
        if self.initialized:
            return

        self.screen.force_update()
        self.icons = {}
        seed_ts = long(time())
        for window in ifilter(self._is_eligible_window, self.screen.get_windows()):
            task = self._window_to_task(window, seed_ts)
            self.tasks[task['id']] = task
            self.windows_by_task_id[task['id']] = window

        log.info('ScreenManager initialized with %d tasks and %d icons.',
            len(self.tasks), len(self.icons))
    
        self.screen.connect('window-opened', self._on_window_open)
        self.screen.connect('window-closed', self._on_window_close)
        self.initialized = True

    def list_tasks(self):
        self._ensure_initialized()
        return self.tasks.values()

    def get_icon(self, icon_id):
        self._ensure_initialized()
        return self.icons.get(icon_id, {})

    def activate_task(self, task_id):
        self._ensure_initialized()
        if task_id in self.windows_by_task_id:
            window = self.windows_by_task_id[task_id]

            def impl():
                window.activate(long(time()))

            GLib.idle_add(impl)

            return True

        return False


