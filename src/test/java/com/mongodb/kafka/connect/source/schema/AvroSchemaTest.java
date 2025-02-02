/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.kafka.connect.source.schema;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;

import io.confluent.connect.avro.AvroConverter;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class AvroSchemaTest {

  @Test
  @DisplayName("test supports valid avro schema")
  void testSupportsValidAvroSchema() {
    String schema =
        "{\"type\": \"record\", \"name\":\"Interop\", \"namespace\": \"org.apache.avro\","
            + " \"fields\": ["
            + " {\"name\": \"intField\", \"type\": \"int\"},"
            + " {\"name\": \"longField\", \"type\": \"long\"},"
            + " {\"name\": \"stringField\", \"type\": \"string\", \"default\" : \"MISSING\"},"
            + " {\"name\": \"boolField\", \"type\": \"boolean\"},"
            + " {\"name\": \"floatField\", \"type\": \"float\"},"
            + " {\"name\": \"doubleField\", \"type\": \"double\"},"
            + " {\"name\": \"bytesField\", \"type\": \"bytes\"},"
            + " {\"name\": \"arrayField\", \"type\": {\"type\": \"array\", \"items\": \"double\"}},"
            + " {\"name\": \"mapField\", \"type\": {\"type\": \"map\", \"values\":"
            + "  {\"type\": \"record\", \"name\": \"Foo\","
            + "   \"fields\": [{\"name\": \"label\", \"type\": \"string\"}]}}},"
            + "   {\"name\": \"unionField\", \"type\":"
            + "    [{\"type\": \"array\", \"items\": \"bytes\"}, \"null\"]},"
            + " {\"name\": \"recordField\", \"type\": {\"type\": \"record\", \"name\": \"Node\", "
            + "  \"fields\": ["
            + "   {\"name\": \"label\", \"type\": \"string\"},"
            + "   {\"name\": \"children\", \"type\": {\"type\": \"array\", \"items\": \"Node\"}}"
            + " ]}, \"default\": {\"label\": \"default\", \"children\": []}},"
            + " {\"name\": \"nodeRecordField\", \"type\": {\"type\": \"record\", \"name\": \"Parent\", "
            + "  \"fields\": ["
            + "   {\"name\": \"label\", \"type\": \"string\"},"
            + "   {\"name\": \"parent\", \"type\": \"Node\"}"
            + " ]}}"
            + " ]"
            + "}";

    Schema actual = AvroSchema.fromJson(schema);

    SchemaBuilder nodeBuilder =
        SchemaBuilder.struct().name("org.apache.avro.Node").field("label", Schema.STRING_SCHEMA);
    nodeBuilder.field("children", SchemaBuilder.array(nodeBuilder).build());
    nodeBuilder.defaultValue(
        new Struct(nodeBuilder).put("label", "default").put("children", new ArrayList<>()));
    Schema expected =
        SchemaBuilder.struct()
            .name("org.apache.avro.Interop")
            .field("intField", Schema.INT32_SCHEMA)
            .field("longField", Schema.INT64_SCHEMA)
            .field("stringField", SchemaBuilder.string().defaultValue("MISSING").build())
            .field("boolField", Schema.BOOLEAN_SCHEMA)
            .field("floatField", Schema.FLOAT32_SCHEMA)
            .field("doubleField", Schema.FLOAT64_SCHEMA)
            .field("bytesField", Schema.BYTES_SCHEMA)
            .field("arrayField", SchemaBuilder.array(Schema.INT64_SCHEMA))
            .field(
                "mapField",
                SchemaBuilder.map(
                    Schema.STRING_SCHEMA,
                    SchemaBuilder.struct()
                        .name("org.apache.avro.Foo")
                        .field("label", Schema.STRING_SCHEMA)
                        .build()))
            .field(
                "unionField", SchemaBuilder.array(Schema.OPTIONAL_BYTES_SCHEMA).optional().build())
            .field("recordField", nodeBuilder)
            .field(
                "nodeRecordField",
                SchemaBuilder.struct()
                    .name("org.apache.avro.Parent")
                    .field("label", Schema.STRING_SCHEMA)
                    .field("parent", nodeBuilder))
            .build();

    SchemaUtils.assertSchemaEquals(expected, actual);
  }

  @Test
  @DisplayName("test unsupported avro schema definitions")
  void testUnsupportedAvroSchema() {
    assertAll(
        "Unsupported schema definitions",
        () -> {
          Executable enumSchema =
              createSchema(
                  "{\"type\": \"record\", \"name\": \"EnumTest\","
                      + "  \"fields\": ["
                      + "    {\"name\": \"enumField\", \"type\": {"
                      + "        \"name\": \"enum\", \"type\": \"enum\","
                      + "        \"symbols\" : [\"ONE\", \"TWO\", \"THREE\"]}}]"
                      + "}");
          ConnectException thrown = assertThrows(ConnectException.class, enumSchema);
          assertEquals(
              "Field 'enumField' is invalid. Unsupported Avro schema type: 'ENUM'. "
                  + "The connector will not validate the values. Use string instead.",
              thrown.getMessage());
        },
        () -> {
          Executable fixedSchema =
              createSchema(
                  "{\"type\": \"record\", \"name\": \"FixedTest\","
                      + "  \"fields\": ["
                      + "       {\"name\": \"fixedField\", \"type\":"
                      + "       {\"type\": \"fixed\", \"name\": \"MD5\", \"size\": 16}}"
                      + "]}");
          ConnectException thrown = assertThrows(ConnectException.class, fixedSchema);
          assertEquals(
              "Field 'fixedField' is invalid. Unsupported Avro schema type: 'FIXED'. "
                  + "The connector will not validate the length. Use bytes instead.",
              thrown.getMessage());
        },
        () -> {
          Executable mapSchema =
              createSchema(
                  "{\"type\": \"record\", \"name\": \"MapTest\","
                      + "  \"fields\": ["
                      + "    {\"name\": \"mapField\","
                      + "     \"type\": {\"type\": \"map\", \"values\": \"null\"}}]"
                      + "}");
          ConnectException thrown = assertThrows(ConnectException.class, mapSchema);
          assertEquals(
              "Field 'mapField' is invalid. Unsupported Avro schema type: 'NULL'.",
              thrown.getMessage());
        },
        () -> {
          Executable unionSchema =
              createSchema(
                  "{\"type\": \"record\", \"name\": \"unionTest1\","
                      + "  \"fields\": ["
                      + "    {\"name\": \"unionField\","
                      + "     \"type\": [{\"type\": \"string\"}]}]"
                      + "}");
          ConnectException thrown = assertThrows(ConnectException.class, unionSchema);
          assertEquals(
              "Field 'unionField' is invalid. Union Schemas are not supported, "
                  + "unless one value is null to represent an optional value.",
              thrown.getMessage());
        },
        () -> {
          Executable unionSchema =
              createSchema(
                  "{\"type\": \"record\", \"name\": \"unionTest2\","
                      + "  \"fields\": ["
                      + "    {\"name\": \"unionField\","
                      + "     \"type\": [{\"type\": \"string\"}, {\"type\": \"int\"}]}]"
                      + "}");
          ConnectException thrown = assertThrows(ConnectException.class, unionSchema);
          assertEquals(
              "Field 'unionField' is invalid. Union Schemas are not supported, "
                  + "unless one value is null to represent an optional value.",
              thrown.getMessage());
        },
        () -> {
          Executable unionSchema =
              createSchema(
                  "{\"type\": \"record\", \"name\": \"unionTest3\","
                      + "  \"fields\": ["
                      + "    {\"name\": \"unionField\","
                      + "     \"type\": [\"null\", {\"type\": \"enum\", \"name\": \"myEnum\","
                      + "        \"symbols\" : [\"ONE\", \"TWO\", \"THREE\"]}]}]"
                      + "}");
          ConnectException thrown = assertThrows(ConnectException.class, unionSchema);
          assertEquals(
              "Field 'unionField' is invalid. Union Schema contains an unsupported Avro schema type: 'ENUM'. "
                  + "The connector will not validate the values. Use string instead.",
              thrown.getMessage());
        },
        () -> {
          Executable unionSchema =
              createSchema(
                  "{\"type\": \"record\", \"name\": \"unionTest4\","
                      + "  \"fields\": ["
                      + "    {\"name\": \"unionField\","
                      + "     \"type\": [{\"type\": \"map\", \"values\": \"null\"}, \"null\"]}]"
                      + "}");
          ConnectException thrown = assertThrows(ConnectException.class, unionSchema);
          assertEquals(
              "Field 'unionField' is invalid. Union Schema contains an unsupported Avro schema type: 'MAP', "
                  + "which contains an unsupported Avro schema type: 'NULL'.",
              thrown.getMessage());
        });
  }

  @Test
  @DisplayName("test nested field support")
  void testNestedFieldSupport() {
    Schema actual =
        AvroSchema.fromJson(
            "{\n"
                + "  \"type\": \"record\",\n"
                + "  \"name\": \"keySchema\",\n"
                + "  \"fields\" : [{\"name\": \"fullDocument.documentKey\", \"type\": \"string\", \"default\": \"MISSING\"}]\n"
                + "}");

    Schema expected =
        SchemaBuilder.struct()
            .name("keySchema")
            .field(
                "fullDocument.documentKey", SchemaBuilder.string().defaultValue("MISSING").build())
            .build();

    SchemaUtils.assertSchemaEquals(expected, actual);
  }

  @Test
  @DisplayName("test invalid avro schema definitions")
  void testInvalidAvroSchema() {
    assertAll(
        "Error scenarios",
        () -> assertThrows(ConnectException.class, createSchema(null)),
        () -> assertThrows(ConnectException.class, createSchema("[]")),
        () -> assertThrows(ConnectException.class, createSchema("{}")));
  }

  @Test
  void testAvroSchemaParsingAndConversion() {
    // parse JSON Avro schema and assert that it is parsed correctly
    String jsonAvroSchema =
        "{\n"
            + "  \"name\": \"recordSchemaName\",\n"
            + "  \"namespace\": \"recordSchemaNamespace\",\n"
            + "  \"type\": \"record\",\n"
            + "  \"fields\": [\n"
            + "    {\n"
            + "      \"name\": \"nestedRecordFieldName\",\n"
            + "      \"type\": {\n"
            + "        \"name\": \"nested.record.schema.namespace.nestedRecordSchemaName\",\n"
            // Since the `name` is specified as a fullname, the `namespace` must be ignored,
            // but its presence is valid.
            // See https://avro.apache.org/docs/current/spec.html#names for the details.
            + "        \"namespace\": \"mustBeIgnored\",\n"
            + "        \"type\": \"record\",\n"
            + "        \"fields\": [\n"
            + "          {\n"
            + "            \"name\": \"fieldName\",\n"
            + "            \"type\": \"int\"\n"
            + "          }\n"
            + "        ]\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    Schema kafkaSchema = AvroSchema.fromJson(jsonAvroSchema);
    SchemaBuilder expectedKafkaSchemaBuilder =
        SchemaBuilder.struct()
            .name("recordSchemaNamespace.recordSchemaName")
            .field(
                "nestedRecordFieldName",
                SchemaBuilder.struct()
                    .name("nested.record.schema.namespace.nestedRecordSchemaName")
                    .field("fieldName", SchemaBuilder.INT32_SCHEMA)
                    .build());
    assertEquals(expectedKafkaSchemaBuilder.build(), kafkaSchema);

    // convert Kafka schema and value to their Avro representation
    Struct kafkaValue =
        new Struct(kafkaSchema)
            .put(
                "nestedRecordFieldName",
                new Struct(kafkaSchema.field("nestedRecordFieldName").schema())
                    .put("fieldName", 1));
    SchemaAndValue kafkaSchemaAndValue = new SchemaAndValue(kafkaSchema, kafkaValue);
    AvroConverter avroConverter = mockAvroConverter();
    byte[] avroEncodedSchemaAndValue =
        avroConverter.fromConnectData(
            "topicName", kafkaSchemaAndValue.schema(), kafkaSchemaAndValue.value());

    // convert `avroEncodedSchemaAndValue` back to a Kafka schema and value
    Schema expectedVersionedKafkaSchema =
        expectedKafkaSchemaBuilder
            // We have to specify the version because this is what `AvroConverter.fromConnectData`
            // (or rather the Schema Registry it uses via `SchemaRegistryClient`) did before
            // producing `avroEncodedSchemaAndValue`.
            .version(1)
            .build();
    Struct expectedVersionedKafkaValue =
        new Struct(expectedVersionedKafkaSchema)
            .put(
                "nestedRecordFieldName",
                new Struct(expectedVersionedKafkaSchema.field("nestedRecordFieldName").schema())
                    .put("fieldName", 1));
    SchemaAndValue expectedVersionedSchemaAndValue =
        new SchemaAndValue(expectedVersionedKafkaSchema, expectedVersionedKafkaValue);
    assertEquals(
        expectedVersionedSchemaAndValue,
        avroConverter.toConnectData("topicName", avroEncodedSchemaAndValue));
  }

  private Executable createSchema(final String jsonSchema) {
    return () -> AvroSchema.validateJsonSchema(jsonSchema);
  }

  private static AvroConverter mockAvroConverter() {
    final MockSchemaRegistryClient schemaRegistry = new MockSchemaRegistryClient();
    final AvroConverter avroConverter = new AvroConverter(schemaRegistry);
    avroConverter.configure(
        Collections.singletonMap("schema.registry.url", "http://fake-url"), false);
    return avroConverter;
  }
}
