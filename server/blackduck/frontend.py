from gi.repository import Gtk, GLib


class BlackDuckDisplay(Gtk.Window):
    def __init__(self, eventbus):
        super(BlackDuckDisplay, self).__init__(default_width=320, default_height=240, title='BlackDuck Service')

        textview = Gtk.TextView()
        self.textbuffer = textview.get_buffer()
        scrolled = Gtk.ScrolledWindow()
        scrolled.add(textview)
        self.add(scrolled)

        eventbus.register_callback('log-message', self.on_log_message)

    def append_text(self, text):
        iter_ = self.textbuffer.get_end_iter()
        self.textbuffer.insert(iter_, "[%s] %s\n" % (str(time()), text))

    def on_log_message(self, logmsg):
        GLib.idle_add(self.append_text, 'LOG: {0}'.format(logmsg))

