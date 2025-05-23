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

package org.citygml4j.tools.option;

import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

public class CityGMLOutputOptions implements Option {
    @CommandLine.Option(names = {"-o", "--output"}, paramLabel = "<dir>",
            description = "Store output files in this directory.")
    private Path outputDirectory;

    @CommandLine.Option(names = "--output-encoding", defaultValue = "UTF-8",
            description = "Encoding to use for output files (default: ${DEFAULT-VALUE}).")
    private String encoding;

    @CommandLine.Option(names = "--no-pretty-print", negatable = true, defaultValue = "true",
            description = "Format and indent output files (default: ${DEFAULT-VALUE}).")
    private boolean prettyPrint;

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public String getEncoding() {
        return encoding;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (outputDirectory != null) {
            outputDirectory = outputDirectory.toAbsolutePath().normalize();
            if (Files.isRegularFile(outputDirectory)) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The --output '" + outputDirectory + "' exists but is not a directory");
            }
        }
    }
}
