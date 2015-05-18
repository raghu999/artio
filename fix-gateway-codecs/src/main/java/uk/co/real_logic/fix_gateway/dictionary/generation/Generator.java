/*
 * Copyright 2015 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.dictionary.generation;

import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.collections.IntHashSet;
import uk.co.real_logic.agrona.generation.OutputManager;
import uk.co.real_logic.fix_gateway.dictionary.StandardFixConstants;
import uk.co.real_logic.fix_gateway.dictionary.ir.*;
import uk.co.real_logic.fix_gateway.dictionary.ir.Entry.Element;
import uk.co.real_logic.fix_gateway.fields.DecimalFloat;
import uk.co.real_logic.fix_gateway.fields.LocalMktDateEncoder;
import uk.co.real_logic.fix_gateway.fields.UtcTimestampEncoder;
import uk.co.real_logic.fix_gateway.util.AsciiFlyweight;
import uk.co.real_logic.fix_gateway.util.MutableAsciiFlyweight;
import uk.co.real_logic.sbe.generation.java.JavaUtil;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static uk.co.real_logic.fix_gateway.dictionary.generation.AggregateType.MESSAGE;
import static uk.co.real_logic.fix_gateway.dictionary.generation.GenerationUtil.importFor;
import static uk.co.real_logic.fix_gateway.dictionary.generation.GenerationUtil.importStaticFor;
import static uk.co.real_logic.sbe.generation.java.JavaUtil.formatPropertyName;

public abstract class Generator
{

    protected static final String MSG_TYPE = "MsgType";
    public static final String EXPAND_INDENT = ".toString().replace(\"\\n\", \"\\n  \")";

    protected String commonCompoundImports(final String form)
    {
        return String.format(
            "    private Header%s header = new Header%1$s();\n\n" +
            "    public Header%1$s header() {\n" +
            "        return header;\n" +
            "    }\n\n" +

            "    private Trailer%1$s trailer = new Trailer%1$s();\n\n" +
            "    public Trailer%1$s trailer() {\n" +
            "        return trailer;\n" +
            "    }\n\n",
            form);
    }

    private static final String COMMON_COMPOUND_IMPORTS =
            "import %1$s.Header%4$s;\n" +
            "import %1$s.Trailer%4$s;\n";

    protected final Dictionary dictionary;
    protected final String builderPackage;
    protected final OutputManager outputManager;

    protected Generator(final Dictionary dictionary, final String builderPackage, final OutputManager outputManager)
    {
        this.dictionary = dictionary;
        this.builderPackage = builderPackage;
        this.outputManager = outputManager;
    }

    public void generate()
    {
        generateAggregate(dictionary.header(), AggregateType.HEADER);
        generateAggregate(dictionary.trailer(), AggregateType.TRAILER);

        dictionary.messages()
                .forEach(msg -> generateAggregate(msg, AggregateType.MESSAGE));
    }

    protected abstract void generateAggregate(final Aggregate aggregate, final AggregateType type);

    protected String generateClassDeclaration(
        final String className,
        final AggregateType type,
        final Class<?> parent,
        final Class<?> topType)
    {
        return String.format(
            importFor(MutableDirectBuffer.class) +
            importStaticFor(CodecUtil.class) +
            importStaticFor(StandardFixConstants.class) +
            importFor(parent) +
            (type == MESSAGE ? COMMON_COMPOUND_IMPORTS : "") +
            importFor(DecimalFloat.class) +
            importFor(MutableAsciiFlyweight.class) +
            importFor(AsciiFlyweight.class) +
            importFor(LocalMktDateEncoder.class) +
            importFor(UtcTimestampEncoder.class) +
            importFor(StandardCharsets.class) +
            importFor(Arrays.class) +
            importFor(IntHashSet.class) +
            "\npublic class %2$s implements %3$s\n" +
            "{\n\n",
            builderPackage,
            className,
            parent.getSimpleName(),
            topType.getSimpleName());
    }

    protected String generateResetMethod(List<Entry> entries)
    {
        final String resetCalls = entries
            .stream()
            .filter(entry -> !entry.required())
            .map(this::resetCall)
            .collect(joining());

        return String.format(
            "    public void reset() {\n" +
            "%s" +
            "    }\n\n",
            resetCalls
        );
    }

    protected String resetMethodName(final String name)
    {
        return "reset" + name;
    }

    private String resetCall(final Entry entry)
    {
        if (entry.element() instanceof Field)
        {
            return String.format(
                "        %1$s();\n",
                resetMethodName(entry.name())
            );
        }
        else
        {
            return "";
        }
    }

    protected String optionalField(final Entry entry)
    {
        final String name = entry.name();
        return entry.required()
             ? ""
             : String.format(
                "    private boolean has%1$s;\n\n" +
                "    public void %2$s()\n" +
                "    {\n" +
                "        has%1$s = false;\n" +
                "    }\n\n",
                name,
                resetMethodName(name));
    }

    protected String generateToString(Aggregate aggregate, final boolean hasCommonCompounds)
    {
        final String entriesToString =
                aggregate.entries()
                        .stream()
                        .map(this::generateEntryToString)
                        .collect(joining(" + \n"));

        final String prefix =
            !hasCommonCompounds ? ""
            : "\"  \\\"header\\\": \" + header" + EXPAND_INDENT + " + \"\\n\" + ";

        final String suffix =
            !(aggregate instanceof Group && getClass() == EncoderGenerator.class) ? ""
            : "        if (next != null)\n" +
              "        {\n" +
              "            entries += \",\\n\" + next.toString();\n" +
              "        }\n";

        return String.format(
                "    public String toString()\n" +
                "    {\n" +
                "        String entries =%1$s\n" +
                "%2$s;\n\n" +
                "        entries = \"{\\n  \\\"MsgType\\\": \\\"%4$s\\\",\\n\" + entries + \"}\";\n" +
                "%3$s" +
                "        return entries;\n" +
                "    }\n\n",
                prefix,
                entriesToString,
                suffix,
                aggregate.name());
    }

    protected String generateEntryToString(final Entry entry)
    {
        //"  \"OnBehalfOfCompID\": \"abc\",\n" +

        final Element element = entry.element();
        final String name = entry.name();
        if (element instanceof Field)
        {
            final Field field = (Field) element;
            final String value = generateValueToString(field);

            final String formatter = String.format(
                "String.format(\"  \\\"%s\\\": \\\"%%s\\\",\\n\", %s)",
                name,
                value
            );

            return "             " + (entry.required() ? formatter : String.format("(has%s ? %s : \"\")", name, formatter));
        }
        else if (element instanceof Group && getClass() == EncoderGenerator.class)
        // TODO: remove this restriction groups for decoders are implemented
        {
            return String.format(
                "                (%2$s != null ? String.format(\"  \\\"%1$s\\\": [\\n" +
                "  %%s" +
                "\\n  ]" +
                "\\n\", %2$s" + EXPAND_INDENT + ") : \"\")",
                name,
                formatPropertyName(name)
            );
        }

        return "\"\"";
    }

    protected String generateValueToString(Field field)
    {
        final String fieldName = JavaUtil.formatPropertyName(field.name());
        switch (field.type())
        {
            case STRING:
                return generateStringToString(fieldName);

            case DATA:
                return String.format("Arrays.toString(%s)", fieldName);

            default:
                return fieldName;
        }
    }

    protected abstract String generateStringToString(String fieldName);

}
