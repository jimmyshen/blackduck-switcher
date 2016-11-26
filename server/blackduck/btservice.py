# BlackDuck Bluetooth service.
#
# Author: Jimmy Shen <jimmyshen@slothbucket.com>

import bluetooth
import logging

from threading import Thread

SERVICE_NAME = 'BlackDuckTaskService'
SERVICE_UUID = '7f759fe2-b22a-11e6-ba35-37c9859e1514'

log = logging.getLogger(__name__)


class BluetoothService(Thread):
    def __init__(self, eventbus, screen):
        super(BluetoothService, self).__init__(name='BluetoothService')
        self.daemon = True
        self.eventbus = eventbus
        self.screen_manager = screen_manager
        self.handlers = {
            'list_tasks': self._handle_list_tasks,
            'get_icon': self._handle_get_icon,
            'activate_task': self._handle_activate_task,
        }

    def make_response(self, status, **kwargs):
        response = {'status': status}
        response.update(kwargs)
        return response

    def make_client_error_response(self, msg, *fmtargs):
        if fmtargs:
            msg = msg % (*fmtargs)

        log.error(msg)
        return make_response('client-error', error=msg)

    def make_server_error_response(self, exc, msg, *fmtargs):
        if fmtargs:
            msg = msg % (*fmtargs)

        log.exception(msg, exc_info=exc)
        return make_response('server-error', error=': '.join([msg, exc.message]))

    def make_ok_response(self, payload=None):
        return make_response('ok', data=payload or {})

    def handle_message(self, msg):
        if 'command' not in msg:
            return make_client_error_response('Missing "command" in message.')
        elif msg['command'] not in handlers:
            return make_client_error_response('Received unrecognized command "%s"', msg['command'])
        else:
            payload = msg.get('payload', {})
            handler = self.dispatch_table[msg['command_name']]
            return handler(payload)

    def _handle_list_tasks(self, payload):
        try:
            return make_ok_response({'tasks': self.screen_manager.list_tasks()})
        except Exception as e:
            return make_server_error_response(e, 'Could not list tasks')

    def _handle_list_task_updates(self, payload):
        if 'last_update_ts' not in payload:
            return make_client_error_response('Missing "last_update_ts" in payload.')

        try:
            client_ts = long(payload['last_update_ts'])
            tasks = []
            for task in self.screen_manager.list_tasks():
                if task['last_update_ts'] > client_ts:
                    tasks.append(task)

            return make_ok_response({'tasks': tasks})
        except Exception as e:
            return make_server_error_response(e, 'Could not get task updates since %s', payload['last_update_ts'])

    def _handle_batchget_icon(self, payload):
        if 'icon_ids' not in payload:
            return make_client_error_response('Missing "icon_ids" in payload.')

        try:
            icons = {icon_id: self.screen_manager.get_icon(icon_id) for icon_id in payload['icon_ids']}
            return make_ok_response({'icons': icons})
        except Exception as e:
            return make_server_error_response(e, 'Could not get icon with ID %s', payload['icon_id'])

    def _handle_activate_task(self, payload):
        if 'task_id' not in payload:
            return make_client_error_response('Missing "task_id" in payload.')

        try:
            task_id = payload['task_id']
            self.screen_manager.activate_task(task_id)
            return make_ok_response()
        except Exception as e:
            return make_server_error_response(e, 'Could not activate task with ID %s', payload['task_id'])

    def manage_connection(self, client_sock, client_addr):
        try:
            # Duckypatch in a read() method to make msgpack
            # stream deserializer happy...
            client_sock.read = lambda bytelen: client_sock.recv(bytelen)

            while True:
                msg = msgpack.unpack(client_sock)
                response = msgpack.pack(self.handle_message(msg))
                log.info('Wrote back %d bytes to client.', len(response))
                client_sock.write(response)
        finally:
            client_sock.close()
            log.info('Connection with "%s" was closed.', client_info)

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
                self.trace('INFO', 'Waiting for connection from client...')
                client_sock, client_addr = sock.accept()
                manage_connection(client_sock, client_addr)
        except KeyboardInterrupt:
            self.trace('INFO', '\b\bUser requested termination.')
        finally:
            sock.close()
            bluetooth.stop_advertising(sock)
            self.trace('INFO', 'Bluetooth service stopped.')


