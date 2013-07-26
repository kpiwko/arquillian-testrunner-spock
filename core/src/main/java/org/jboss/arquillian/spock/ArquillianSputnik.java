/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.arquillian.spock;

import org.jboss.arquillian.test.spi.TestRunnerAdaptor;
import org.jboss.arquillian.test.spi.TestRunnerAdaptorBuilder;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.spockframework.runtime.Sputnik;

/**
 * @author <a href="mailto:kpiwko@redhat.com">Karel Piwko</a>
 *
 * Extension to Sputnik class that allows to mimic Before and After Suite events
 */
public class ArquillianSputnik extends Sputnik {

    public ArquillianSputnik(Class<?> clazz) throws InitializationError {
        super(clazz);
        State.runnerStarted();
    }

    @Override
    public void run(RunNotifier notifier) {

        // first time we're being initialized
        if (!State.hasTestAdaptor()) {
            // no, initialization has been attempted before and failed, refuse to do anything else
            if (State.hasInitializationException()) {
                // failed on suite level, ignore children
                // notifier.fireTestIgnored(getDescription());
                notifier.fireTestFailure(new Failure(getDescription(), new RuntimeException(
                        "Arquillian has previously been attempted initialized, but failed. See cause for previous exception",
                        State.getInitializationException())));
            } else {
                TestRunnerAdaptor adaptor = TestRunnerAdaptorBuilder.build();
                try {
                    // don't set it if beforeSuite fails
                    adaptor.beforeSuite();
                    State.testAdaptor(adaptor);
                } catch (Exception e) {
                    // caught exception during BeforeSuite, mark this as failed
                    State.caughtInitializationException(e);
                    notifier.fireTestFailure(new Failure(getDescription(), e));
                }
            }
        }
        notifier.addListener(new RunListener() {
            @Override
            public void testRunFinished(Result result) throws Exception {
                State.runnerFinished();
                shutdown();
            }

            private void shutdown() {
                try {
                    if (State.isLastRunner()) {
                        try {
                            if (State.hasTestAdaptor()) {
                                TestRunnerAdaptor adaptor = State.getTestAdaptor();
                                adaptor.afterSuite();
                                adaptor.shutdown();
                            }
                        } finally {
                            State.clean();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Could not run @AfterSuite", e);
                }
            }
        });
        // initialization ok, run children
        if (State.hasTestAdaptor()) {
            super.run(notifier);
        }
    }
}
