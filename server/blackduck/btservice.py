# BlackDuck Bluetooth service.
#
# Author: Jimmy Shen <jimmyshen@slothbucket.com>

import bluetooth
import msgpack
import logging as log

from Queue import Queue
from threading import Thread

SERVICE_NAME = 'BlackDuckService'
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
    SCALE_TASK = 'scale_task'


class Context(object):
    __slots__ = ['request_id', 'command', 'screen_manager']

    def __init__(self, request_id, command, screen_manager):
        self.request_id = request_id
        self.command = command
        self.screen_manager = screen_manager


class Handler(object):
    def __init__(self, context):
        self.context = context

    def response(self, status, **kwargs):
        response = {'status': status, 'request_id': self.context.request_id}
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
        return self.response(Status.OK, payload=payload or {})

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
            return self.server_error_response(e, 'Internal error trying to list tasks.')


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
            return self.server_error_response(e, 'Internal error listing task updates.')


class BatchGetIconsHandler(Handler):
    def __init__(self, context):
        super(BatchGetIconsHandler, self).__init__(context)

    def handle(self, payload):
        if 'icon_ids' not in payload:
            return self.client_error_response('Missing "icon_ids" in payload.')

        try:
            icons = []
            for icon_id in payload['icon_ids']:
                icon = self.context.screen_manager.get_icon(icon_id)
                if not icon:
                    return self.client_error_response('Could not find icon with ID %s', icon_id)

                icons.append(icon)
            return self.ok_response({'icons': icons})
        except Exception as e:
            return self.server_error_response(e, 'Internal error fetching icons.')


class ActivateTaskHandler(Handler):
    def __init__(self, context):
        super(ActivateTaskHandler, self).__init__(context)

    def handle(self, payload):
        if 'task_id' not in payload:
            return self.client_error_response('Missing "task_id" in payload.')

        try:
            task_id = payload['task_id']
            self.context.screen_manager.activate_task(task_id)
            return self.ok_response()
        except Exception as e:
            return self.server_error_response(e, 'Internal error activating task.')


class ScaleTaskHandler(Handler):
    def __init__(self, context):
        super(ScaleTaskHandler, self).__init__(context)

    def handle(self, payload):
        if 'task_id' not in payload:
            return self.client_error_response('Missing "task_id" in payload.')
        elif 'scale_action' not in payload:
            return self.client_error_response('Missing "action" in payload.')
        elif payload['scale_action'] not in ('maximize', 'unmaximize'):
            return self.client_error_response(
                'Invalid options for "scale_action"; must be either "maximize" or "unmaximize"')

        try:
            task_id = payload['task_id']
            scale_action = payload['scale_action']
            if scale_action == 'maximize':
                self.context.screen_manager.maximize_task(task_id)
            elif scale_action == 'unmaximize':
                self.context.screen_manager.unmaximize_task(task_id)
            return self.ok_response()
        except Exception as e:
            return self.server_error_response(e, 'Internal error scaling task.')


class HandlerFactory(object):
    def __init__(self, parent):
        self.parent = parent
        self.handlers = {
            Command.LIST_TASKS: ListTasksHandler,
            Command.LIST_UPDATED_TASKS: ListUpdatedTasksHandler,
            Command.BATCHGET_ICONS: BatchGetIconsHandler,
            Command.ACTIVATE_TASK: ActivateTaskHandler,
            Command.SCALE_TASK: ScaleTaskHandler,
        }

    def create(self, command_name, request_id):
        builder = self.handlers.get(command_name, BlackHoleHandler)
        return builder(Context(request_id, command_name, self.parent.screen_manager))


class BluetoothService(Thread):
    def __init__(self, screen_manager):
        super(BluetoothService, self).__init__(name='BluetoothService')
        self.daemon = True
        self.screen_manager = screen_manager
        self.handler_factory = HandlerFactory(self)
        self.unpacker = msgpack.Unpacker()
        self.requests = Queue()

    def _read_socket(self, client_sock):
        while True:
            buf = client_sock.recv(128)
            if not buf:
                return False

            self.unpacker.feed(buf)
            for request in self.unpacker:
                self.requests.put(request)

            if not self.requests.empty():
                return True

    def _process_request(self, msg):
        command = msg.get('command', '')
        request_id = msg.get('request_id', 0xDEADBEEF)
        payload = msg.get('payload', {})
        handler = self.handler_factory.create(command, request_id)
        return handler.handle(payload)

    def manage_connection(self, client_sock, client_addr):
        try:
            while True:
                log.info('Waiting for requests...')
                if not self._read_socket(client_sock):
                    continue

                request = self.requests.get()
                log.debug('Received request from client %s:\n%s', client_addr, request)
                response = self._process_request(request)
                client_sock.send(msgpack.packb(response))
                log.debug('Sent message to client %s:\n%s', client_addr, response)
        except bluetooth.BluetoothError as e:
            log.error('Bluetooth error while handling connection.', exc_info=e)
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
                self.manage_connection(client_sock, client_addr)
        except KeyboardInterrupt:
            log.info('User requested termination.')
        finally:
            bluetooth.stop_advertising(sock)
            sock.close()
            log.info('Bluetooth service stopped.')


