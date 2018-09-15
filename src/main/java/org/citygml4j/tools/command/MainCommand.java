package org.citygml4j.tools.command;

import org.citygml4j.CityGMLContext;
import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.model.citygml.ade.binding.ADEContext;
import org.citygml4j.tools.common.log.LogLevel;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.util.Constants;
import org.citygml4j.tools.util.URLClassLoader;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.stream.Stream;

@CommandLine.Command(name = "citygml-tools",
        description = "Collection of tools for processing CityGML files.",
        version = {"hallo", "1.0", "bla"},
        mixinStandardHelpOptions = true,
        subcommands = {
                CommandLine.HelpCommand.class,
                GlobalAppMoverCommand.class,
                TextureClipperCommand.class,
                FromCityJSONCommand.class,
                ToCityJSONCommand.class
        })
public class MainCommand implements CityGMLTool {

    @CommandLine.Option(names = "--log", description = "Log level: debug, info, warn, error (default: ${DEFAULT-VALUE}).")
    private String logLevel = "info";

    private CityGMLBuilder cityGMLBuilder;

    @Override
    public boolean execute() throws Exception {
        Logger log = Logger.getInstance();
        log.setLogLevel(LogLevel.fromValue(logLevel));

        log.info("Starting citygml-tools.");
        CityGMLContext context = CityGMLContext.getInstance();

        // search for ADE extensions
        URLClassLoader classLoader = new URLClassLoader(MainCommand.class.getClassLoader());
        try {
            Path adeExtensionsDir = Constants.APP_HOME.resolve(Constants.ADE_EXTENSIONS_DIR);
            if (Files.exists(adeExtensionsDir)) {
                try (Stream<Path> stream = Files.walk(adeExtensionsDir)
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".jar"))) {
                    stream.forEach(classLoader::addPath);
                }
            }

            if (classLoader.getURLs().length > 0) {
                log.info("Loading ADE extensions.");
                ServiceLoader<ADEContext> adeLoader = ServiceLoader.load(ADEContext.class, classLoader);

                for (ADEContext adeContext : adeLoader) {
                    log.debug("Registering ADE extension '" + adeContext.getClass() + "'.");
                    context.registerADEContext(adeContext);
                }
            }
        } catch (IOException e) {
            log.error("Failed to initialize ADE extensions.", e);
            return false;
        }

        log.info("Initializing application environment.");
        cityGMLBuilder = context.createCityGMLBuilder(classLoader);

        return true;
    }

    public CityGMLBuilder getCityGMLBuilder() {
        return cityGMLBuilder;
    }

    @Override
    public void validate() throws CommandLine.ParameterException {
        // nothing to do
    }
}
