/*
 * Copyright © 2017-2020 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.store.registry.validation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.LinkedList;
import org.aspectj.lang.ProceedingJoinPoint;
import org.factcast.core.Fact;
import org.factcast.core.FactValidationException;
import org.junit.jupiter.api.*;

public class FactValidationAspectTest {

  final ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);

  final FactValidator v = mock(FactValidator.class);

  final FactValidationAspect uut = new FactValidationAspect(v);

  final Fact f = Fact.builder().ns("ns").type("type").version(1).buildWithoutPayload();

  @Test
  void testInterceptPublish() throws Throwable {

    when(jp.getArgs()).thenReturn(new Object[] {Collections.singletonList(f)});
    when(v.validate(f)).thenReturn(new LinkedList<>());

    uut.interceptPublish(jp);

    verify(jp).proceed();
  }

  @Test
  void testInterceptPublishConditional() throws Throwable {

    when(jp.getArgs()).thenReturn(new Object[] {Collections.singletonList(f)});
    when(v.validate(f)).thenReturn(new LinkedList<>());

    uut.interceptPublishIfUnchanged(jp);

    verify(jp).proceed();
  }

  @Test
  void testInterceptPublishPropagatesErros() throws Throwable {

    when(jp.getArgs()).thenReturn(new Object[] {Collections.singletonList(f)});
    when(v.validate(f)).thenReturn(Collections.singletonList(new FactValidationError("doing")));

    try {
      uut.interceptPublish(jp);
      fail();
    } catch (FactValidationException e) {
      // expected
    }
    verify(jp, never()).proceed();
  }
}
