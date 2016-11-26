import logging

from hashlib import md5
from time import time
from functools import partial

log = logging.getLogger(__name__)


class ScreenEventBridge(object):
    """Subscribes to GTK screen events and pushes them to an event bus."""

    def __init__(self, screen, eventbus):
        self.screen = screen
        self.eventbus = eventbus
        self.event_handlers = {
            'window-opened': self._on_window_open,
            'window-closed': self._on_window_close,
        }
        self.connected = set()

    def _on_window_open(self, screen, window):
        GLib.idle_add(self.eventbus.publish, 'window-opened', [window])

    def _on_window_close(self, screen, window):
        GLib.idle_add(self.eventbus.publish, 'window-closed', [window])

    def connect(self, event_name, callback):
        if event_name not in self.event_handlers:
            log.error('Cannot connect unsupported event type "%s"', event_name)
            return

        if event_name not in self.connected:
            self.screen.connect(event_name, self.event_handlers[event_name])
            self.connected.add(event_name)


class ScreenManager(object):
    def __init__(self, screen, eventbus, compress_icons=True):
        self.screen = screen
        self.eventbus = eventbus
        self.initialied = False
        self.compress_icons = compress_icons

        self.tasks = {}
        self.icons = {}

    def _cache_icon(self, icon):
        pixels = icon.get_pixels()

        hasher = md5()
        hasher.update(pixels)
        icon_id = hasher.hexdigest()

        if icon_id not in icons:
            icons[icon_id] = {
                'id': icon_id,
                'width': icon.get_width(),
                'height': icon.get_height(),
                'pixels': pixels.encode('zlib_codec') if self.compress_icons else pixels,
            }

        return icon_id

    def _window_to_task(self, window, last_update_ts, is_open):
        icon_id = self._cache_icon(window.get_icon())

        return {
            'id': str(window.get_xid()),
            'app_name': window.get_application().get_name(),
            'title': window.get_name(),
            'icon_id': icon_id,
            'is_open': is_open,
            'last_update_ts': last_update_ts,
        }

    def _on_window_openclose_change(self, is_open, window):
        task = self._window_to_task(window, time(), is_open)
        self.tasks[task['id']] = task

    def initialize_state(self):
        """Should be called before use."""
        if self.initialized:
            return

        self.screen.force_update()
        self.icons = {}
        seed_ts = time()
        for window in self.screen.get_windows():
            task = self._window_to_task(window, seed_ts, True)
            self.tasks[task['id']] = task

        log.info('ScreenManager initialized with %d tasks and %d icons.',
            len(self.tasks), len(self.icons))

        self.eventbus.register_callback('window-opened', partial(self._on_window_openclose_change, True))
        self.eventbus.register_callback('window-closed', partial(self._on_window_openclose_change, False))

    def list_tasks(self):
        return self.tasks.values()

    def get_icon(self, icon_id):
        return self.icon_cache.get(icon_id, {})

    def activate_task(self, window_id):
        def impl():
            window = self.screen.get_window(window_id)
            window.activate(time())

        GLib.idle_add(impl)


