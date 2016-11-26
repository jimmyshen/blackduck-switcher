import logging

from collections import defaultdict
from threading import Lock


class EventBus(object):
    """Creates an event bus for broadcasting events for consumers
    within this process."""

    def __init__(self):
        self.callbacks = defaultdict(list)
        self.lock = Lock()

    def register_callback(self, event_name, callback):
        self.callbacks[event_name].append(callback)

    def publish(self, event_name, arglist):
        self.lock.acquire()
        try:
            for callback in self.callbacks[event_name]:
                callback(*arglist)
        finally:
            self.lock.release()


class EventBusLogOutput(object):
    """A file-like wrapper for EventBus that can be attached to a
    logging StreamHandler to send events to the application event
    bus."""

    def __init__(self, eventbus):
        self.eventbus = eventbus
        self.msgbuf = []

    def write(self, s):
        self.msgbuf.append(s)

    def flush(self):
        if self.msgbuf:
            self.eventbus.publish('log-message', [''.join(self.msgbuf)])
            self.msgbuf[:] = []

