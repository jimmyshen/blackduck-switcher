# BlackDuck Bluetooth service.
#
# Author: Jimmy Shen <jimmyshen@slothbucket.com>

import bluetooth
import logging as log

from threading import Thread

SERVICE_NAME = 'BlackDuckTaskService'
SERVICE_UUID = '7f759fe2-b22a-11e6-ba35-37c9859e1514'


class BluetoothService(Thread):
    def __init__(self, screen_manager):
        super(BluetoothService, self).__init__(name='BluetoothService')
        self.daemon = True
        self.screen_manager = screen_manager
        self.handlers = {
            'list_tasks': self._handle_list_tasks,
            'list_updated_tasks': self._handle_list_updated_tasks,
            'batchget_icons': self._handle_batchget_icons,
            'activate_task': self._handle_activate_task,
        }

    def _make_response(self, status, **kwargs):
        response = {'status': status}
        response.update(kwargs)
        return response

    def _make_client_error_response(self, msg, *fmtargs):
        if fmtargs:
            msg = msg % fmtargs

        log.error(msg)
        return _make_response('client-error', error=msg)

    def _make_server_error_response(self, exc, msg, *fmtargs):
        if fmtargs:
            msg = msg % fmtargs

        log.exception(msg, exc_info=exc)
        return _make_response('server-error', error=': '.join([msg, exc.message]))

    def _make_ok_response(self, payload=None):
        return _make_response('ok', data=payload or {})

    def handle_message(self, msg):
        if 'command' not in msg:
            return _make_client_error_response('Missing "command" in message.')
        elif msg['command'] not in handlers:
            return _make_client_error_response('Received unrecognized command "%s"', msg['command'])
        else:
            payload = msg.get('payload', {})
            handler = self.dispatch_table[msg['command_name']]
            return handler(payload)

    def _handle_list_tasks(self, payload):
        try:
            return _make_ok_response({'tasks': self.screen_manager.list_tasks()})
        except Exception as e:
            return _make_server_error_response(e, 'Could not list tasks')

    def _handle_list_updated_tasks(self, payload):
        if 'last_update_ts' not in payload:
            return _make_client_error_response('Missing "last_update_ts" in payload.')

        try:
            client_ts = long(payload['last_update_ts'])
            tasks = []
            for task in self.screen_manager.list_tasks():
                if task['last_update_ts'] > client_ts:
                    tasks.append(task)

            return _make_ok_response({'tasks': tasks})
        except Exception as e:
            return _make_server_error_response(e, 'Could not get task updates since %s', payload['last_update_ts'])

    def _handle_batchget_icons(self, payload):
        if 'icon_ids' not in payload:
            return _make_client_error_response('Missing "icon_ids" in payload.')

        try:
            icons = [self.screen_manager.get_icon(icon_id) for icon_id in payload['icon_ids']]
            return _make_ok_response({'icons': icons})
        except Exception as e:
            return _make_server_error_response(e, 'Could not get icon with ID %s', payload['icon_id'])

    def _handle_activate_task(self, payload):
        if 'task_id' not in payload:
            return _make_client_error_response('Missing "task_id" in payload.')

        try:
            task_id = payload['task_id']
            self.screen_manager.activate_task(task_id)
            return _make_ok_response()
        except Exception as e:
            return _make_server_error_response(e, 'Could not activate task with ID %s', payload['task_id'])

    def manage_connection(self, client_sock, client_addr):
        class SocketFileImpl(object):
            # Give socket a file-like API to make msgpack stream deserializer happy.
            def read(_, bytelen):
                data = client_sock.recv(bytelen)
                log.debug('Read %d bytes from client %s.', len(data), client_addr)
                return data

        try:
            while True:
                msg = msgpack.unpack(SocketFileImpl())
                response = msgpack.pack(self.handle_message(msg))
                log.debug('Writing %d bytes to client %s.', len(response), client_addr)
                client_sock.write(response)
        finally:
            client_sock.close()
            log.info('Connection with client %s closed.', client_addr)

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
        log.debug('Advertising Bluetooth service as "%s"', SERVICE_NAME)

        try:
            while True:
                log.info('Waiting for connection from client...')
                client_sock, client_addr = sock.accept()
                manage_connection(client_sock, client_addr)
        except KeyboardInterrupt:
            log.info('User requested termination.')
        finally:
            sock.close()
            try:
                bluetooth.stop_advertising(sock)
            except Exception:
                pass
            log.info('Bluetooth service stopped.')


