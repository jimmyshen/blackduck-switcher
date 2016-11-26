import logging as log
from collections import defaultdict
from threading import Lock, current_thread


class EventBus(object):
    """Creates an event bus for broadcasting events for consumers
    within this process."""

    def __init__(self):
        self.callbacks = defaultdict(list)
        self.lock = Lock()

    def register_callback(self, event_name, callback):
        self.callbacks[event_name].append(callback)

    def publish(self, event_name, arglist):
        # NOTE: Do not log in here unless you want to recurse yourself to kingdom come.
        t = current_thread()
        print 'Waiting for lock in thread', t.ident, t.name
        self.lock.acquire()
        print 'Publishing event ("%s", %s) to bus.' % (event_name, arglist)
        try:
            for callback in self.callbacks[event_name]:
                callback(*arglist)
        finally:
            self.lock.release()
            print 'Lock released by thread', t.ident, t.name

