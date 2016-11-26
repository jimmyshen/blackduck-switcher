import logging as log
from gi.repository import Gtk, GLib


class BlackDuckLogDisplay(Gtk.Window):
    """GTK dialog for displaying BlackDuck log messages. Exposes a file-like interface so
    it can just be plugged in as another logging handler."""

    def __init__(self):
        super(BlackDuckLogDisplay, self).__init__(default_width=640, default_height=480, title='BlackDuck Service')

        # TODO: Prettify.
        textview = Gtk.TextView()
        self.textbuffer = textview.get_buffer()
        scrolled = Gtk.ScrolledWindow()
        scrolled.add(textview)
        self.add(scrolled)

        self.logbuffer = []

    def write(self, logmsg):
        print 'Received', logmsg
        # TODO: Can we use textbuffer directly?
        self.logbuffer.append(logmsg.format(logmsg))

    def flush(self):
        def impl():
            self.textbuffer.insert(self.textbuffer.get_end_iter(), ''.join(self.logbuffer))
            self.logbuffer[:] = []

        GLib.idle_add(impl)

