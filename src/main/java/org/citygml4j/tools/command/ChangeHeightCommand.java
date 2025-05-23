/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2025 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.command;

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityGMLOutputVersion;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOptions;
import org.citygml4j.tools.util.GlobalObjectsReader;
import org.citygml4j.tools.util.HeightChanger;
import org.citygml4j.tools.util.ResourceProcessor;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(name = "change-height",
        description = "Change the height values of city objects by a given offset.")
public class ChangeHeightCommand extends CityGMLTool {
    @CommandLine.Mixin
    private InputOptions inputOptions;

    @CommandLine.Option(names = {"-z", "--offset"}, paramLabel = "<double>", required = true,
            description = "Offset to add to height values.")
    private double offset;

    @CommandLine.Option(names = {"-m", "--mode"}, defaultValue = "relative",
            description = "Height mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private HeightChanger.Mode mode;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    OverwriteInputOptions overwriteOptions;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    private final String suffix = "__changed_height";

    @Override
    public Integer call() throws ExecutionException {
        if (offset == 0 && mode == HeightChanger.Mode.RELATIVE) {
            log.warn("Height values do not change for offset = " + offset + " and mode = " + mode + ".");
            log.info("Cancelling the operation.");
            return CommandLine.ExitCode.OK;
        }

        List<InputFile> inputFiles = getInputFiles(inputOptions, suffix);
        if (inputFiles.isEmpty()) {
            return CommandLine.ExitCode.OK;
        }

        CityGMLInputFactory in = createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityGMLOutputFactory out = createCityGMLOutputFactory(version.getVersion());

        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);
            OutputFile outputFile = getOutputFile(inputFile, suffix, outputOptions, overwriteOptions);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile + ".");

            HeightChanger heightChanger = HeightChanger.of(offset).withMode(mode);

            log.debug("Reading implicit geometries from input file.");
            heightChanger.withTemplateGeometries(GlobalObjectsReader.onlyImplicitGeometries()
                    .read(inputFile, getCityGMLContext())
                    .getTemplateGeometries());

            try (CityGMLReader reader = createCityGMLReader(in, inputFile, inputOptions);
                 ResourceProcessor resourceProcessor = ResourceProcessor.of(inputFile, outputFile)) {
                if (!version.isSetVersion()) {
                    setCityGMLVersion(reader, out);
                }

                if (outputFile.isTemporary()) {
                    log.debug("Writing temporary output file " + outputFile + ".");
                } else {
                    log.info("Writing output to file " + outputFile + ".");
                }

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(getFeatureInfo(reader))) {
                    log.debug("Reading city objects and changing their height values.");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        if (!(feature instanceof Appearance)) {
                            heightChanger.changeHeight(feature);
                        }

                        resourceProcessor.process(feature);
                        writer.writeMember(feature);
                    }
                }
            } catch (CityGMLReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile + ".", e);
            } catch (CityGMLWriteException e) {
                throw new ExecutionException("Failed to write file " + outputFile + ".", e);
            }

            if (outputFile.isTemporary()) {
                replaceInputFile(inputFile, outputFile);
            }
        }

        return CommandLine.ExitCode.OK;
    }
}
