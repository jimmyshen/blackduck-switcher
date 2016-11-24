#!/usr/bin/env python
# Advertises a Bluetooth server that sends a simple message every 5s.

import bluetooth
import hashlib
import logging

SERVICE_NAME = 'BlackDuckTaskService'
SERVICE_UUID = '7f759fe2-b22a-11e6-ba35-37c9859e1514'

log = logging.getLogger(__name__)

class Server(object):
    def run(self):
        sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
        sock.bind(("", bluetooth.PORT_ANY))
        sock.listen(1)

        bluetooth.advertise_service(
            sock,
            SERVICE_NAME,
            service_id = SERVICE_UUID,
            service_classes = [SERVICE_UUID, bluetooth.SERIAL_PORT_CLASS],
            profiles = [bluetooth.SERIAL_PORT_PROFILE])

        try:
            while True:
                client_sock, client_info = sock.accept()
        except KeyboardInterrupt:
            print "\b\bUser requested termination."
        finally:
            sock.close()
            bluetooth.stop_advertising(sock)
            print "Bluetooth server stopped."


