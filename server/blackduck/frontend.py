import logging
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

    def configure_logger(self, logger, fmt=None, datefmt=None):
        """Registers a StreamHandler on the logger that outputs to this dialog."""

        class StreamImpl(object):
            def write(_, logmsg):
                # TODO: Can we use textbuffer directly?
                self.logbuffer.append(logmsg)

            def flush(_):
                def impl():
                    self.textbuffer.insert(self.textbuffer.get_end_iter(), ''.join(self.logbuffer))
                    self.logbuffer[:] = []

                GLib.idle_add(impl)

        handler = logging.StreamHandler(StreamImpl())
        handler.setFormatter(logging.Formatter(fmt, datefmt))
        logger.addHandler(handler)

