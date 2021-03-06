#!/usr/bin/python
#
#  Copyright 2002-2019 Barcelona Supercomputing Center (www.bsc.es)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

# -*- coding: utf-8 -*-

"""
PyCOMPSs API - IO
=====================
    This file contains the class constraint, needed for the IO task
    definition through the decorator.
"""

import inspect
import os
from functools import wraps
import pycompss.util.context as context
from pycompss.api.commons.error_msgs import not_in_pycompss
from pycompss.util.arguments import check_arguments

if __debug__:
    import logging
    logger = logging.getLogger(__name__)

MANDATORY_ARGUMENTS = {}
SUPPORTED_ARGUMENTS = {''}
DEPRECATED_ARGUMENTS = {''}


class IO(object):
    """
    This decorator also preserves the argspec, but includes the __init__ and
    __call__ methods, useful on IO task creation.
    """

    def __init__(self, *args, **kwargs):
        """
        Store arguments passed to the decorator
        # self = itself.
        # args = not used.
        # kwargs = dictionary with the given constraints.

        :param args: Arguments
        :param kwargs: Keyword arguments
        """
        self.args = args
        self.kwargs = kwargs
        self.scope = context.in_pycompss()
        self.registered = False
        # This enables the decorator to get info from the caller
        # (e.g. self.source_frame_info.filename or
        #  self.source_frame_info.lineno)
        # self.source_frame_info = inspect.getframeinfo(inspect.stack()[1][0])
        if self.scope:
            if __debug__:
                logger.debug("Init @IO decorator...")

            # Check the arguments
            check_arguments(MANDATORY_ARGUMENTS,
                            DEPRECATED_ARGUMENTS,
                            SUPPORTED_ARGUMENTS | DEPRECATED_ARGUMENTS,
                            list(kwargs.keys()), "@IO")

    def __call__(self, func):
        """
        Parse and set the IO parameters within the task core element.

        :param func: Function to decorate
        :return: Decorated function.
        """
        @wraps(func)
        def io_f(*args, **kwargs):
            if not self.scope:
                # from pycompss.api.dummy.binary import binary as dummy_binary
                # d_b = dummy_binary(self.args, self.kwargs)
                # return d_b.__call__(func)
                raise Exception(not_in_pycompss("IO"))

            if context.in_master():
                # master code
                mod = inspect.getmodule(func)
                self.module = mod.__name__  # not func.__module__

                if (self.module == '__main__' or
                        self.module == 'pycompss.runtime.launch'):
                    # The module where the function is defined was run as
                    # __main__, so we need to find out the real module name.

                    # path = mod.__file__
                    # dirs = mod.__file__.split(os.sep)
                    # file_name = os.path.splitext(
                    #                 os.path.basename(mod.__file__))[0]

                    # Get the real module name from our launch.py variable
                    path = getattr(mod, "APP_PATH")

                    dirs = path.split(os.path.sep)
                    file_name = os.path.splitext(os.path.basename(path))[0]
                    mod_name = file_name

                    i = len(dirs) - 1
                    while i > 0:
                        new_l = len(path) - (len(dirs[i]) + 1)
                        path = path[0:new_l]
                        if "__init__.py" in os.listdir(path):
                            # directory is a package
                            i -= 1
                            mod_name = dirs[i] + '.' + mod_name
                        else:
                            break
                    self.module = mod_name

                # Include the registering info related to @IO
                if not self.registered:
                    # Set as registered
                    self.registered = True
                    # Retrieve the base core_element established at @task
                    # decorator
                    from pycompss.api.task import current_core_element as cce
                    # Update the core element information with the IO
                    # argument information

                    cce.set_impl_io(True)
            else:
                # worker code
                pass
            # This is executed only when called.
            if __debug__:
                logger.debug("Executing IO_f wrapper.")

            if len(args) > 0:
                # The 'self' for a method function is passed as args[0]
                slf = args[0]

                # Replace and store the attributes
                saved = {}
                for k, v in self.kwargs.items():
                    if hasattr(slf, k):
                        saved[k] = getattr(slf, k)
                        setattr(slf, k, v)

            # Call the method
            import pycompss.api.task as t
            t.prepend_strings = False
            ret = func(*args, **kwargs)
            t.prepend_strings = True

            if len(args) > 0:
                # Put things back
                for k, v in saved.items():
                    setattr(slf, k, v)

            return ret

        io_f.__doc__ = func.__doc__
        return io_f


# ########################################################################### #
# ###################### IO DECORATOR ALTERNATIVE NAME ###################### #
# ########################################################################### #

io = IO
