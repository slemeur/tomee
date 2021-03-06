/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.server.httpd;

import org.apache.openejb.server.ServerService;

public class HttpServerFactory {

    public static ServerService createServerService() {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            cl.loadClass("org.mortbay.jetty.Connector");
            return new JettyHttpEjbServer();
        } catch (Throwable e) {
            // We don't have jetty
        }

        return new OpenEJBHttpEjbServer();
    }
}
