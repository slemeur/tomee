/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.threads.task;

import org.apache.openejb.OpenEJBRuntimeException;
import org.apache.openejb.core.ThreadContext;
import org.apache.openejb.core.ivm.ClientSecurity;
import org.apache.openejb.core.security.AbstractSecurityService;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.spi.SecurityService;

import javax.security.auth.login.LoginException;
import java.util.concurrent.Callable;

public abstract class CUTask<T> extends ManagedTaskListenerTask {
    private static final SecurityService SECURITY_SERVICE = SystemInstance.get().getComponent(SecurityService.class);

    private final Context initialContext;

    public CUTask(final Object task) {
        super(task);

        Object stateTmp = SECURITY_SERVICE.currentState();
        final boolean associate;
        if (stateTmp == null) {
            stateTmp = ClientSecurity.getIdentity();
            associate = stateTmp != null;
        } else {
            associate = false;
        }
        final ThreadContext threadContext = ThreadContext.getThreadContext();
        initialContext = new Context(
            associate, stateTmp, threadContext == null ? null : threadContext.get(AbstractSecurityService.SecurityContext.class),
            threadContext, Thread.currentThread().getContextClassLoader());
    }

    protected T invoke(final Callable<T> call) throws Exception {
        initialContext.enter();

        Throwable throwable = null;
        try {
            taskStarting(future, executor, delegate); // do it in try to avoid issues if an exception is thrown
            return call.call();
        } catch (final Throwable t) {
            throwable = t;
            taskAborted(throwable);
            return rethrow(t);
        } finally {
            taskDone(future, executor, delegate, throwable);

            initialContext.exit();
        }
    }

    private T rethrow(final Throwable t) throws Exception {
        if (Exception.class.isInstance(t)) {
            throw Exception.class.cast(t);
        } else if (Error.class.isInstance(t)) {
            throw Error.class.cast(t);
        }
        throw new OpenEJBRuntimeException(t.getMessage(), t);
    }

    public static final class Context {
        /*
        private static final Class<?>[] THREAD_SCOPES = new Class<?>[] {
                RequestScoped.class, SessionScoped.class, ConversationScoped.class
        };
        */

        private final Object securityServiceState;
        private final ThreadContext threadContext;
        private final ClassLoader loader;
        private final boolean associate;
        private final AbstractSecurityService.SecurityContext securityContext;

        /* propagation of CDI context seems wrong
        private final CdiAppContextsService contextService;
        private final CdiAppContextsService.State cdiState;
        */

        private Context currentContext;

        private Context(final boolean associate, final Object initialSecurityServiceState,
                        final AbstractSecurityService.SecurityContext securityContext, final ThreadContext initialThreadContext,
                        final ClassLoader initialLoader) {
            this.associate = associate;
            this.securityServiceState = initialSecurityServiceState;
            this.securityContext = securityContext;
            this.threadContext = initialThreadContext;
            this.loader = initialLoader;

            /* propagation of CDI context seems wrong
            final ContextsService genericContextsService = WebBeansContext.currentInstance().getContextsService();
            if (CdiAppContextsService.class.isInstance(genericContextsService)) {
                contextService = CdiAppContextsService.class.cast(genericContextsService);
                cdiState = contextService.saveState();
            } else {
                contextService = null;
                cdiState = null;
            }
            */
        }

        public void enter() {
            final Thread thread = Thread.currentThread();

            final ClassLoader oldCl = thread.getContextClassLoader();
            thread.setContextClassLoader(loader);

            final Object threadState;
            if (associate) {
                //noinspection unchecked
                try {
                    SECURITY_SERVICE.associate(securityServiceState);
                } catch (final LoginException e) {
                    throw new IllegalStateException(e);
                }
                threadState = null;
            } else {
                threadState = SECURITY_SERVICE.currentState();
                SECURITY_SERVICE.setState(securityServiceState);
            }

            final ThreadContext oldCtx;
            if (threadContext != null) {
                final ThreadContext newContext = new ThreadContext(threadContext);
                newContext.set(Context.class, this);
                if (securityContext != null) {
                    newContext.set(AbstractSecurityService.ProvidedSecurityContext.class, new AbstractSecurityService.ProvidedSecurityContext(securityContext));
                }
                oldCtx = ThreadContext.enter(newContext);
            } else {
                oldCtx = null;
            }

            currentContext = new Context(associate, threadState, securityContext, oldCtx, oldCl);

            /* propagation of CDI context seems wrong
            if (cdiState != null) {
                contextService.restoreState(cdiState);
            }
            */
        }

        public void exit() {
            if (currentContext.threadContext != null) {
                ThreadContext.exit(currentContext.threadContext);
            }

            if (!associate) {
                SECURITY_SERVICE.setState(currentContext.securityServiceState);
            } else {
                SECURITY_SERVICE.disassociate();
            }

            /* propagation of CDI context seems wrong
            if (currentContext.cdiState != null) {
                contextService.restoreState(currentContext.cdiState);
                contextService.removeThreadLocals();
            }
            */

            Thread.currentThread().setContextClassLoader(currentContext.loader);
            currentContext = null;
        }
    }
}
