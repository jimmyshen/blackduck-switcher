import logging

from hashlib import md5
from time import time, sleep
from threading import Thread
from collections import defaultdict
from Queue import Queue

import gi
gi.require_versions({'Gtk': '3.0', 'Wnck': '3.0'})
from gi.repository import GObject, GLib, Gtk, Wnck

log = logging.getLogger(__name__)


class ScreenEventBus(object):
    argmappers = {
        'window-opened': lambda screen, window: (window,),
        'window-closed': lambda screen, window: (window,)
    }

    def __init__(self, screen=None):
        self.screen = screen or Wnck.Screen.get_default()
        self.callbacks = defaultdict(list)

    def _event_dispatcher(self, event_name, event_argmapper):
        def dispatcher(*event_args):
            args = event_argmapper(*event_args)
            for callback in self.callbacks[event_name]:
                GLib.idle_add(callback, *args)

        return dispatcher

    def _register_callback(self, event_name, callback):
        if event_name not in self.callbacks:
            self.screen.connect(event_name, self._event_dispatcher(event_name, self.argmappers[event_name]))

        self.callbacks[event_name].append(callback)

    def on_window_open(self, callback):
        self._register_callback('window-opened', callback)

    def on_window_close(self, callback):
        self._register_callback('window-closed', callback)


class ServiceInfoDialog(Gtk.Window):
    def __init__(self, screen):
        super(AppDialog, self).__init__(default_width=320, default_height=240, title='BlackDuck Service')

        textview = Gtk.TextView()
        self.textbuffer = textview.get_buffer()
        scrolled = Gtk.ScrolledWindow()
        scrolled.add(textview)

        self.add(scrolled)

    def append_text(self, text):
        iter_ = self.textbuffer.get_end_iter()
        self.textbuffer.insert(iter_, "[%s] %s\n" % (str(time()), text))

    def log_window_opened(self, window):
        self.append_text('Window opened: {0} ({1})'.format(window.get_xid(), window.get_name()))

    def log_window_closed(self, window):
        self.append_text('Window closed: {0} ({1})'.format(window.get_xid(), window.get_name()))


if __name__ == '__main__':
    try:
        dialog = ServiceInfoDialog(Wnck.Screen.get_default())
        screen_events = ScreenEventBus()
        screen_events.on_window_open(app_dialog.log_window_opened)
        screen_events.on_window_close(app_dialog.log_window_closed)

        dialog.show_all()
        dialog.connect('delete-event', Gtk.main_quit)
        Gtk.main()
    finally:
        Wnck.shutdown()

