
from collections import namedtuple

Task = namedtuple('Task', ['id', 'name', 'icon_id', 'state'])

TaskIcon = namedtuple('TaskIcon', ['id', 'width', 'height', 'bitmap'])
