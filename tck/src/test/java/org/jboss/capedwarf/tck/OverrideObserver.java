/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.capedwarf.tck;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ExtensionDef;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.core.spi.EventContext;
import org.jboss.arquillian.test.spi.TestResult;
import org.jboss.arquillian.test.spi.TestResult.Status;
import org.jboss.arquillian.test.spi.annotation.TestScoped;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.arquillian.test.spi.event.suite.Before;
import org.jboss.arquillian.test.spi.event.suite.Test;
import org.jboss.arquillian.test.spi.event.suite.TestEvent;

/**
 * /**
 * Extension that overrides After/Before/Test execution of methods
 * based on regexp or methods set configured in arquillian.xml
 * <p/>
 * <arquillian>
 * <extension qualifier="override">
 * <property name="expression">.*</property>
 * <property name="methods">org.acme.foo.ListTest#testStrFilter,org.bar.boo.QwertTest#testFoo</property>
 * <property name="methods_1">org.acme.foo.ListTest#testIntFilter</property>
 * </extension>
 * </arquillian>
 *
 * @author Aslak Knutsen
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class OverrideObserver {
    private final Logger log = Logger.getLogger(OverrideExtension.class.getName());

    public static final String EXTENSION_NAME = "override";
    public static final String EXTENSION_PROPERTY_EXP = "expression";
    public static final String EXTENSION_PROPERTY_METHODS = "methods";
    public static final String EXTENSION_IGNORE_ALL = "IGNORE_ALL";

    @Inject
    private Instance<ArquillianDescriptor> desciptorInst;

    @Inject
    @TestScoped
    private InstanceProducer<TestResult> testResultProducer;

    private String expression = null;
    private Set<String> methods;

    public void before(@Observes(precedence = 1000) EventContext<Before> context) {
        execute(context, "before");
    }

    public void after(@Observes(precedence = 1000) EventContext<After> context) {
        execute(context, "after");
    }

    public void test(@Observes(precedence = 1000) EventContext<Test> context) {
        if (execute(context, "test") == false) {
            testResultProducer.set(new TestResult(Status.SKIPPED));
        }
    }

    private boolean execute(EventContext<? extends TestEvent> context, String phase) {
        if (shouldPerformExecution(context.getEvent())) {
            context.proceed();
            return true;
        } else {
            log.info("Ignore test [" + phase + "]: " + toFqn(context.getEvent()));
            return false;
        }
    }

    private boolean shouldPerformExecution(TestEvent event) {
        return !shouldCancelExecution(event);
    }

    private boolean shouldCancelExecution(TestEvent event) {
        String fqn = toFqn(event);
        return fqn.matches(getExpression()) || getMethods().contains(fqn);
    }

    private String toFqn(TestEvent event) {
        Object test = event.getTestInstance();
        Method method = event.getTestMethod();
        return test.getClass().getName() + "#" + method.getName();
    }

    private String getExpression() {
        if (expression == null) {
            String tmp = readExpressionFromConfiguration();
            if (tmp == null) {
                tmp = EXTENSION_IGNORE_ALL;
            }
            expression = tmp;
        }
        return expression;
    }

    private Set<String> getMethods() {
        if (methods == null) {
            methods = readMethodsFromConfiguration();
        }
        return methods;
    }

    private String readExpressionFromConfiguration() {
        ArquillianDescriptor descriptor = desciptorInst.get();
        for (ExtensionDef def : descriptor.getExtensions()) {
            if (def.getExtensionName().equalsIgnoreCase(EXTENSION_NAME)) {
                String exp = def.getExtensionProperties().get(EXTENSION_PROPERTY_EXP);
                if (exp != null) {
                    return exp;
                }
            }
        }
        return null;
    }

    private Set<String> readMethodsFromConfiguration() {
        final Set<String> set = new HashSet<>();
        ArquillianDescriptor descriptor = desciptorInst.get();
        for (ExtensionDef def : descriptor.getExtensions()) {
            if (def.getExtensionName().equalsIgnoreCase(EXTENSION_NAME)) {
                Map<String,String> properties = def.getExtensionProperties();

                String exp = properties.get(EXTENSION_PROPERTY_METHODS);
                if (exp != null) {
                    set.addAll(Arrays.asList(exp.split(",")));
                }

                for (String key : properties.keySet()) {
                    if (key.startsWith(EXTENSION_PROPERTY_METHODS + "_")) {
                        set.add(properties.get(key));
                    }
                }
            }
        }
        return set;
    }
}