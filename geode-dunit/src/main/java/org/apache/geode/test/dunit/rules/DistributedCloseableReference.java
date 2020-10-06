/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geode.test.dunit.rules;

import static org.apache.geode.test.dunit.VM.DEFAULT_VM_COUNT;
import static org.apache.geode.util.internal.UncheckedUtils.uncheckedCast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DistributedCloseableReference is a JUnit Rule that provides automated tearDown for a static
 * reference in every distributed test {@code VM}s including the main JUnit controller {@code VM}.
 * If the referenced value is an {@code AutoCloseable} or {@code Closeable} then it will be
 * auto-closed and set to null in every {@code VM} during tear down.
 *
 * <p>
 * If the referenced value is not an {@code AutoCloseable} or {@code Closeable}, the
 * {@code DistributedCloseableReference} will use reflection to invoke any method named
 * {@code close}, {@code disconnect}, or {@code stop} regardless of what interfaces are implemented
 * unless {@code autoClose} is set to false.
 *
 * <p>
 * If the referenced value is null in any {@code VM} then it will be ignored in that {@code VM}
 * during tear down.
 *
 * <p>
 * In the following example, every {@code VM} has a {@code ServerLauncher} which will be
 * auto-stopped and set to null during tear down:
 *
 * <pre>
 * {@literal @}Rule
 * public DistributedCloseableReference&lt;ServerLauncher&gt; server = new DistributedCloseableReference&lt;&gt;();
 *
 * {@literal @}Before
 * public void setUp() throws IOException {
 *   Properties configProperties = new Properties();
 *   configProperties.setProperty(LOCATORS, DistributedRule.getLocators());
 *
 *   for (VM vm : toArray(getVM(0), getVM(1), getVM(2), getVM(3), getController())) {
 *     vm.invoke(() -> {
 *       server.set(new ServerLauncher.Builder()
 *           .setMemberName("server" + getVMId())
 *           .setDisableDefaultServer(true)
 *           .setWorkingDirectory(temporaryFolder.newFolder("server" + getVMId()).getAbsolutePath())
 *           .build());
 *
 *       server.get().start();
 *     });
 *   }
 * }
 *
 * {@literal @}Test
 * public void eachVmHasItsOwnServerCache() {
 *   for (VM vm : toArray(getVM(0), getVM(1), getVM(2), getVM(3), getController())) {
 *     vm.invoke(() -> {
 *       assertThat(server.get().getCache()).isNotNull();
 *     });
 * }
 * </pre>
 *
 * <p>
 * In the following example, every {@code VM} has a {@code Cache} which will be auto-closed and set
 * to null during tear down:
 *
 * <pre>
 * {@literal @}Rule
 * public DistributedCloseableReference&lt;Cache&gt; cache = new DistributedCloseableReference&lt;&gt;();
 *
 * {@literal @}Before
 * public void setUp() {
 *   Properties configProperties = new Properties();
 *   configProperties.setProperty(LOCATORS, DistributedRule.getLocators());
 *
 *   for (VM vm : toArray(getVM(0), getVM(1), getVM(2), getVM(3), getController())) {
 *     vm.invoke(() -> {
 *       cache.set(new CacheFactory(configProperties).create());
 *     });
 *   }
 * }
 *
 * {@literal @}Test
 * public void eachVmHasItsOwnCache() {
 *   for (VM vm : toArray(getVM(0), getVM(1), getVM(2), getVM(3), getController())) {
 *     vm.invoke(() -> {
 *       assertThat(cache.get()).isNotNull();
 *     });
 *   }
 * }
 * </pre>
 *
 * <p>
 * In the following example, every {@code VM} has a {@code DistributedSystem} which will be
 * auto-disconnected and set to null during tear down:
 *
 * <pre>
 * {@literal @}Rule
 * public DistributedCloseableReference&lt;DistributedSystem&gt; system = new DistributedCloseableReference&lt;&gt;();
 *
 * {@literal @}Before
 * public void setUp() {
 *   Properties configProperties = new Properties();
 *   configProperties.setProperty(LOCATORS, DistributedRule.getLocators());
 *
 *   for (VM vm : toArray(getVM(0), getVM(1), getVM(2), getVM(3), getController())) {
 *     vm.invoke(() -> {
 *       system.set(DistributedSystem.connect(configProperties));
 *     });
 *   }
 * }
 *
 * {@literal @}Test
 * public void eachVmHasItsOwnDistributedSystemConnection() {
 *   for (VM vm : toArray(getVM(0), getVM(1), getVM(2), getVM(3), getController())) {
 *     vm.invoke(() -> {
 *       assertThat(system.get()).isNotNull();
 *     });
 *   }
 * }
 * </pre>
 *
 * <p>
 * To disable auto-closing in a test, specify {@code autoClose(false)}:
 *
 * <pre>
 * {@literal @}Rule
 * public DistributedCloseableReference&lt;ServerLauncher&gt; serverLauncher =
 *     new DistributedCloseableReference&lt;&gt;().autoClose(false);
 * </pre>
 *
 * <p>
 * The {@code DistributedCloseableReference} value will still be set to null during tear down even
 * if auto-closing is disabled.
 */
@SuppressWarnings({"serial", "unused", "WeakerAccess"})
public class DistributedCloseableReference<V> extends AbstractDistributedRule {

  private static final AtomicReference<Object> REFERENCE = new AtomicReference<>();

  private final AtomicBoolean autoClose = new AtomicBoolean(true);

  public DistributedCloseableReference() {
    this(DEFAULT_VM_COUNT);
  }

  public DistributedCloseableReference(int vmCount) {
    super(vmCount);
  }

  /**
   * Set false to disable autoClose during tearDown. Default is true.
   */
  public DistributedCloseableReference<V> autoClose(boolean value) {
    autoClose.set(value);
    return this;
  }

  /**
   * Gets the current value.
   *
   * @return the current value
   */
  public V get() {
    return uncheckedCast(REFERENCE.get());
  }

  /**
   * Sets to the given value.
   *
   * @param newValue the new value
   */
  public DistributedCloseableReference<V> set(V newValue) {
    REFERENCE.set(newValue);
    return this;
  }

  @Override
  protected void after() {
    invoker().invokeInEveryVMAndController(this::invokeAfter);
  }

  private void invokeAfter() {
    V value = get();
    if (value == null) {
      return;
    }
    REFERENCE.set(null);

    if (autoClose.get()) {
      autoClose(value);
    }
  }

  private void autoClose(V value) {
    if (value instanceof AutoCloseable) {
      close((AutoCloseable) value);

    } else if (hasMethod(value.getClass(), "close")) {
      invokeMethod(value, "close");

    } else if (hasMethod(value.getClass(), "disconnect")) {
      invokeMethod(value, "disconnect");

    } else if (hasMethod(value.getClass(), "stop")) {
      invokeMethod(value, "stop");
    }
  }

  private static void close(AutoCloseable autoCloseable) {
    try {
      autoCloseable.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean hasMethod(Class<?> objectClass, String methodName) {
    try {
      Method method = objectClass.getMethod(methodName);
      Class<?> returnType = method.getReturnType();
      // currently only supports public method with zero parameters
      if (method.getParameterCount() == 0 &&
          Modifier.isPublic(method.getModifiers())) {
        return true;
      }
    } catch (NoSuchMethodException e) {
      // ignore
    }
    return false;
  }

  private static void invokeMethod(Object object, String methodName) {
    try {
      Method method = object.getClass().getMethod(methodName);
      method.invoke(object);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}