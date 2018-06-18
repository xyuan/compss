/*         
 *  Copyright 2002-2018 Barcelona Supercomputing Center (www.bsc.es)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package es.bsc.compss.invokers;

import java.io.File;
import java.lang.reflect.Method;

import es.bsc.compss.exceptions.JobExecutionException;
import es.bsc.compss.types.execution.Invocation;
import es.bsc.compss.types.execution.InvocationContext;
import es.bsc.compss.types.implementations.MethodImplementation;
import java.lang.reflect.InvocationTargetException;


public class JavaInvoker extends Invoker {

    private static final String ERROR_CLASS_REFLECTION = "Cannot get class by reflection";
    private static final String ERROR_METHOD_REFLECTION = "Cannot get method by reflection";

    private final String className;
    private final String methodName;
    protected final Method method;

    public JavaInvoker(InvocationContext context, Invocation invocation, boolean debug, File taskSandboxWorkingDir, int[] assignedCoreUnits) throws JobExecutionException {
        super(context, invocation, debug, taskSandboxWorkingDir, assignedCoreUnits);

        // Get method definition properties
        MethodImplementation methodImpl = null;
        try {
            methodImpl = (MethodImplementation) this.impl;
        } catch (Exception e) {
            throw new JobExecutionException(ERROR_METHOD_DEFINITION + this.methodType, e);
        }
        this.className = methodImpl.getDeclaringClass();
        this.methodName = methodImpl.getAlternativeMethodName();

        // Use reflection to get the requested method
        this.method = getMethod();
    }

    private Method getMethod() throws JobExecutionException {
        Class<?> methodClass = null;
        Method method = null;
        try {
            methodClass = Class.forName(this.className);
        } catch (ClassNotFoundException e) {
            throw new JobExecutionException(ERROR_CLASS_REFLECTION, e);
        }
        try {
            method = methodClass.getMethod(this.methodName, this.types);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new JobExecutionException(ERROR_METHOD_REFLECTION, e);
        }

        return method;
    }

    @Override
    public Object invokeMethod() throws JobExecutionException {
        Object retValue = null;

        /*if (Tracer.isActivated()) {
            Tracer.emitEvent(Tracer.Event.STORAGE_INVOKE.getId(), Tracer.Event.STORAGE_INVOKE.getType());
        }*/
        
        try {
            LOGGER.info("Invoked " + method.getName() + " of " + target + " in " + context.getHostName());
            retValue = method.invoke(target.getValue(), values);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new JobExecutionException(ERROR_TASK_EXECUTION, e);
        }/* finally {
            if (Tracer.isActivated()) {
                Tracer.emitEvent(Tracer.EVENT_END, Tracer.Event.STORAGE_INVOKE.getType());
            }
        }*/

        return retValue;
    }

}
