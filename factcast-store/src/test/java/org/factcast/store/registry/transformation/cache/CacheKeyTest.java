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
package org.factcast.store.registry.transformation.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.factcast.core.Fact;
import org.junit.jupiter.api.Test;

class CacheKeyTest {

  @Test
  void of() {
    String chainId = "1-2-3";
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");

    String ofFact = CacheKey.of(fact, chainId);
    String ofId = CacheKey.of(fact.id(), fact.version(), chainId);

    assertEquals(ofFact, ofId);
    assertTrue(ofFact.contains(fact.id().toString()));
    assertTrue(ofFact.contains(String.valueOf(fact.version())));
    assertTrue(ofFact.contains(chainId));
  }
}
