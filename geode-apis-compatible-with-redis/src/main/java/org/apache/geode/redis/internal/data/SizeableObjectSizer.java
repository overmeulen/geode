/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.redis.internal.data;

import static org.apache.geode.redis.internal.netty.Coder.narrowLongToInt;

import org.apache.geode.internal.size.ReflectionSingleObjectSizer;
import org.apache.geode.internal.size.SingleObjectSizer;
import org.apache.geode.internal.size.Sizeable;

/**
 * A sizer that allows for efficient sizing of objects that implement the {@link Sizeable}
 * interface, and delegates to {@link ReflectionSingleObjectSizer} otherwise.
 */
public class SizeableObjectSizer {

  private final SingleObjectSizer sizer = new ReflectionSingleObjectSizer();

  public int sizeof(Object object) {
    if (object instanceof Sizeable) {
      return ((Sizeable) object).getSizeInBytes();
    } else {
      return narrowLongToInt(sizer.sizeof(object));
    }
  }
}