/*
 *
 *  Copyright 2013 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.zeno.fastblob;

import com.netflix.zeno.fastblob.io.FastBlobReader;
import com.netflix.zeno.fastblob.io.FastBlobWriter;
import com.netflix.zeno.fastblob.record.FastBlobSchema;
import com.netflix.zeno.fastblob.record.FastBlobSchema.FieldType;
import com.netflix.zeno.fastblob.state.FastBlobTypeDeserializationState;
import com.netflix.zeno.serializer.NFDeserializationRecord;
import com.netflix.zeno.serializer.NFSerializationRecord;
import com.netflix.zeno.serializer.NFTypeSerializer;
import com.netflix.zeno.serializer.SerializerFactory;
import com.netflix.zeno.serializer.common.ListSerializer;
import com.netflix.zeno.serializer.common.MapSerializer;
import com.netflix.zeno.serializer.common.SetSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;


public class UndefinedNullCollectionElementSerializerTest {

    @Test
    public void includesLegitimatelyNullElementsButNotUndefinedElementsInList() throws IOException {
        FastBlobStateEngine stateEngine = new FastBlobStateEngine(new SerializerFactory() {
            public NFTypeSerializer<?>[] createSerializers() {
                return new NFTypeSerializer<?>[] {
                        new ListSerializer<Integer>("FakeIntList", new FakeIntSerializer())
                };
            }
        });

        List<Integer> inList = Arrays.asList(1, 2, 3, null, 4);
        stateEngine.add("FakeIntList", inList, new boolean[] { true });

        serializeAndDeserialize(stateEngine);

        FastBlobTypeDeserializationState<List<Integer>> typeDeserializationState = stateEngine.getTypeDeserializationState("FakeIntList");
        List<Integer> outList = typeDeserializationState.get(0);

        Assert.assertEquals(4, outList.size());
        Assert.assertEquals(1, outList.get(0).intValue());
        Assert.assertEquals(3, outList.get(1).intValue());
        Assert.assertEquals(null, outList.get(2));
        Assert.assertEquals(4, outList.get(3).intValue());

    }

    @Test
    public void includesLegitimatelyNullElementsButNotUndefinedElementsInSet() throws IOException {
        FastBlobStateEngine stateEngine = new FastBlobStateEngine(new SerializerFactory() {
            public NFTypeSerializer<?>[] createSerializers() {
                return new NFTypeSerializer<?>[] {
                        new SetSerializer<Integer>("FakeIntSet", new FakeIntSerializer())
                };
            }
        });

        Set<Integer> inSet = new HashSet<Integer>(Arrays.asList(1, 2, 3, 4));
        Set<Integer> inSet2 = new HashSet<Integer>(Arrays.asList(1, 3, null, 4));
        Set<Integer> inSet3 = new HashSet<Integer>(Arrays.asList(1, 2, 3, null, 4));


        stateEngine.add("FakeIntSet", inSet, new boolean[] { true });
        stateEngine.add("FakeIntSet", inSet2, new boolean[] { true });
        stateEngine.add("FakeIntSet", inSet3, new boolean[] { true });


        serializeAndDeserialize(stateEngine);

        FastBlobTypeDeserializationState<Set<Integer>> typeDeserializationState = stateEngine.getTypeDeserializationState("FakeIntSet");

        Set<Integer> outSet = typeDeserializationState.get(0);
        Assert.assertEquals(3, outSet.size());
        Assert.assertTrue(outSet.contains(1));
        Assert.assertTrue(outSet.contains(3));
        Assert.assertTrue(outSet.contains(4));

        Set<Integer> outSet2 = typeDeserializationState.get(1);
        Assert.assertEquals(4, outSet2.size());
        Assert.assertTrue(outSet2.contains(1));
        Assert.assertTrue(outSet2.contains(3));
        Assert.assertTrue(outSet2.contains(null));
        Assert.assertTrue(outSet2.contains(4));

        Set<Integer> outSet3 = typeDeserializationState.get(2);
        Assert.assertEquals(4, outSet3.size());
        Assert.assertTrue(outSet3.contains(1));
        Assert.assertTrue(outSet3.contains(3));
        Assert.assertTrue(outSet3.contains(null));
        Assert.assertTrue(outSet3.contains(4));

    }

    @Test
    public void doesNotIncludeEntriesWithUndefinedKeysOrValuesInMap() throws IOException {
        FastBlobStateEngine stateEngine = new FastBlobStateEngine(new SerializerFactory() {
            public NFTypeSerializer<?>[] createSerializers() {
                return new NFTypeSerializer<?>[] {
                        new MapSerializer<Integer, Integer>("FakeIntMap", new FakeIntSerializer(), new FakeIntSerializer())
                };
            }
        });

        Map<Integer, Integer> inMap = new HashMap<Integer, Integer>();

        inMap.put(0, 1);
        inMap.put(1, 2);
        inMap.put(2, 3);
        inMap.put(3, 4);

        stateEngine.add("FakeIntMap", inMap, new boolean[] { true });

        serializeAndDeserialize(stateEngine);

        FastBlobTypeDeserializationState<Map<Integer, Integer>> typeDeserializationState = stateEngine.getTypeDeserializationState("FakeIntMap");

        Map<Integer, Integer> outMap = typeDeserializationState.get(0);
        Assert.assertEquals(2, outMap.size());
        Assert.assertEquals(Integer.valueOf(1), outMap.get(0));
        Assert.assertEquals(Integer.valueOf(4), outMap.get(3));
    }




    private void serializeAndDeserialize(FastBlobStateEngine stateEngine) throws IOException {
        stateEngine.prepareForWrite();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FastBlobWriter writer = new FastBlobWriter(stateEngine, 0);
        writer.writeSnapshot(new DataOutputStream(baos));
        FastBlobReader reader = new FastBlobReader(stateEngine);
        reader.readSnapshot(new ByteArrayInputStream(baos.toByteArray()));
    }

    /**
     * This Integer serializer is deliberately unable to deserialize the value "2".
     *
     */
    private class FakeIntSerializer extends NFTypeSerializer<Integer> {

        public FakeIntSerializer() {
            super("FakeInt");
        }

        @Override
        public void doSerialize(Integer value, NFSerializationRecord rec) {
            serializePrimitive(rec, "value", value);
        }

        @Override
        protected Integer doDeserialize(NFDeserializationRecord rec) {
            Integer value = deserializeInteger(rec, "value");

            /// unable to deserialize "2"
            if(value != null && value == 2)
                return null;

            return value;
        }

        @Override
        protected FastBlobSchema createSchema() {
            return schema(
                    field("value", FieldType.INT)
            );
        }

        @Override
        public Collection<NFTypeSerializer<?>> requiredSubSerializers() {
            return serializers();
        }

    }


}
