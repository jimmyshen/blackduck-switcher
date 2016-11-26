# BlackDuck Bluetooth service.
#
# Author: Jimmy Shen <jimmyshen@slothbucket.com>

import bluetooth
import logging as log

from threading import Thread

SERVICE_NAME = 'BlackDuckTaskService'
SERVICE_UUID = '7f759fe2-b22a-11e6-ba35-37c9859e1514'


class Status:
    OK = 'ok'
    CLIENT_ERROR = 'client-error'
    SERVER_ERROR = 'server-error'

class Command:
    LIST_TASKS = 'list_tasks'
    LIST_UPDATED_TASKS = 'list_updated_tasks'
    BATCHGET_ICONS = 'batchget_icons'
    ACTIVATE_TASK = 'activate_task'


class Context(object):
    __slots__ = ['request_id', 'command', 'screen_manager']

    def __init__(self, request_id, command, screen_manager):
        self.request_id = request_id
        self.command = command
        self.screen_manager


class Handler(object):
    def __init__(self, context):
        self.context = context

    def response(self, status, **kwargs):
        response = {'status': status, 'request_id': context.request_id}
        response.update(kwargs)
        return response

    def client_error_response(self, msg, *fmtargs):
        if fmtargs:
            msg = msg % fmtargs

        log.error(msg)
        return self.response(Status.CLIENT_ERROR, error=msg)

    def server_error_response(self, exc, msg, *fmtargs):
        if fmtargs:
            msg = msg % fmtargs

        log.exception(msg, exc_info=exc)
        return self.response(Status.SERVER_ERROR, error=': '.join([msg, exc.message]))

    def ok_response(self, payload=None):
        return self.response(Status.OK, data=payload or {})

    def handle(self, payload):
        raise NotImplementedError()


class BlackHoleHandler(Handler):
    def __init__(self, context):
        super(BlackHoleHandler, self).__init__(context)

    def handle(self, payload):
        return self.client_error_response('Unrecognized command.')


class ListTasksHandler(Handler):
    def __init__(self, context):
        super(ListTasksHandler, self).__init__(context)

    def handle(self, payload):
        try:
            return self.ok_response({'tasks': self.context.screen_manager.list_tasks()})
        except Exception as e:
            return self.server_error_response(e, 'Could not list tasks')


class ListUpdatedTasksHandler(Handler):
    def __init__(self, context):
        super(ListUpdatedTasksHandler, self).__init__(context)

    def handle(self, payload):
        if 'last_update_ts' not in payload:
            return self.client_error_response('Missing "last_update_ts" in payload.')

        try:
            client_ts = long(payload['last_update_ts'])
            tasks = []
            for task in self.context.screen_manager.list_tasks():
                if task['last_update_ts'] > client_ts:
                    tasks.append(task)

            return self.ok_response({'tasks': tasks})
        except Exception as e:
            return self.server_error_response(e, 'Could not get task updates since %s', payload['last_update_ts'])


class BatchGetIconsHandler(Handler):
    def __init__(self, context):
        super(BatchGetIconsHandler, self).__init__(context)

    def handle(self, payload):
        if 'icon_ids' not in payload:
            return self.client_error_response('Missing "icon_ids" in payload.')

        try:
            icons = [self.screen_manager.get_icon(icon_id) for icon_id in payload['icon_ids']]
            return self.ok_response({'icons': icons})
        except Exception as e:
            return self.server_error_response(e, 'Could not get icon with ID %s', payload['icon_id'])


class ActivateTaskHandler(Handler):
    def __init__(self, context):
        super(ActivateTaskHandler, self).__init__(context)

    def handler(self, payload):
        if 'task_id' not in payload:
            return self.client_error_response('Missing "task_id" in payload.')

        try:
            task_id = payload['task_id']
            self.context.screen_manager.activate_task(task_id)
            return self.ok_response()
        except Exception as e:
            return self.server_error_response(e, 'Could not activate task with ID %s', payload['task_id'])


class HandlerFactory(object):
    def __init__(self):
        self.handlers = {
            Command.LIST_TASKS: ListTasksHandler,
            Command.LIST_UPDATED_TASKS: ListUpdatedTasksHandler,
            Command.BATCHGET_ICONS: BatchGetIconsHandler,
            Command.ACTIVATE_TASK: ActivateTaskHandler,
        }

    def create(self, command_name, request_id):
        builder = self.handlers.get(command_name, BlackHoleHandler)
        return builder(request_id)


class BluetoothService(Thread):
    def __init__(self, screen_manager):
        super(BluetoothService, self).__init__(name='BluetoothService')
        self.daemon = True
        self.screen_manager = screen_manager
        self.handler_factory = HandlerFactory()

    def handle_message(self, msg):
        if 'command' not in msg:
            return client_error_response('Missing "command" in message.')
        else:
            command = msg['command']
            request_id = msg.get('request_id', 0xDEADBEEF)
            payload = msg.get('payload', {})
            handler = self.handler_factory.create(command, request_id)
            return handler.handle(payload)

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
                log.info('Accepted connection from client %s.', client_addr)
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


