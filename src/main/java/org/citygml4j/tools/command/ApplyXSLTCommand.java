package org.citygml4j.tools.command;

import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityGMLOutputVersion;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOption;
import org.citygml4j.tools.util.InputFiles;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.transform.TransformerPipeline;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamSource;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@CommandLine.Command(name = "apply-xslt",
        description = "Transforms city objects based on XSLT stylesheets.")
public class ApplyXSLTCommand extends CityGMLTool {
    @CommandLine.Option(names = {"-x", "--xslt"}, split = ",", paramLabel = "<file>", required = true,
            description = "One or more XSLT 1.0 stylesheets to use to transform the individual top-level city " +
                    "objects in the input file(s). The stylesheets are applied in the given order.")
    private Path[] stylesheets;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    OverwriteInputOption overwriteOption;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    private final String suffix = "__transformed";

    @Override
    public Integer call() throws ExecutionException {
        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles = InputFiles.of(inputOptions.getFiles())
                .withFilter(path -> !stripFileExtension(path).endsWith(suffix))
                .find();

        if (inputFiles.isEmpty()) {
            log.warn("No files found at " + inputOptions.joinFiles() + ".");
            return CommandLine.ExitCode.OK;
        }

        log.info("Found " + inputFiles.size() + " file(s) at " + inputOptions.joinFiles() + ".");

        CityGMLInputFactory in = createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityGMLOutputFactory out = createCityGMLOutputFactory(version.getVersion());

        try {
            out.withTransformer(TransformerPipeline.newInstance(Arrays.stream(stylesheets)
                    .map(path -> new StreamSource(path.toFile()))
                    .toArray(Source[]::new)));
        } catch (TransformerConfigurationException e) {
            throw new ExecutionException("Failed to read XSLT stylesheet.", e);
        }

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            Path outputFile = getOutputFile(inputFile, suffix, overwriteOption.isOverwrite());

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile.toAbsolutePath() + ".");

            try (CityGMLReader reader = createCityGMLReader(in, inputFile, inputOptions)) {
                if (!version.isSetVersion()) {
                    setCityGMLVersion(reader, out);
                }

                if (overwriteOption.isOverwrite()) {
                    log.debug("Writing temporary output file " + outputFile.toAbsolutePath() + ".");
                } else {
                    log.info("Writing output to file " + outputFile.toAbsolutePath() + ".");
                }

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(getFeatureInfo(reader))) {
                    log.debug("Reading and transforming city objects using the specified XSLT stylesheet(s).");
                    while (reader.hasNext()) {
                        writer.writeMember(reader.next());
                    }
                }
            } catch (CityGMLReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile.toAbsolutePath() + ".", e);
            } catch (CityGMLWriteException e) {
                throw new ExecutionException("Failed to write file " + outputFile.toAbsolutePath() + ".", e);
            }

            if (overwriteOption.isOverwrite()) {
                log.debug("Replacing input file with temporary output file.");
                replaceInputFile(inputFile, outputFile);
            }
        }

        return CommandLine.ExitCode.OK;
    }
}
