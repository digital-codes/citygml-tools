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

package org.citygml4j.tools.util;

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.common.GeometryInfo;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometryProperty;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class GlobalObjectsReader {
    private final EnumSet<GlobalObjects.Type> types;
    private boolean withTemplateAppearances;

    private GlobalObjectsReader(EnumSet<GlobalObjects.Type> types) {
        this.types = types;
    }

    public static GlobalObjectsReader defaults() {
        return new GlobalObjectsReader(EnumSet.allOf(GlobalObjects.Type.class));
    }

    public static GlobalObjectsReader of(GlobalObjects.Type... types) {
        return new GlobalObjectsReader(EnumSet.copyOf(Arrays.asList(types)));
    }

    public static GlobalObjectsReader of(EnumSet<GlobalObjects.Type> types) {
        return new GlobalObjectsReader(types);
    }

    public static GlobalObjectsReader onlyAppearances() {
        return new GlobalObjectsReader(EnumSet.of(GlobalObjects.Type.APPEARANCE));
    }

    public static GlobalObjectsReader onlyCityObjectGroups() {
        return new GlobalObjectsReader(EnumSet.of(GlobalObjects.Type.CITY_OBJECT_GROUP));
    }

    public static GlobalObjectsReader onlyImplicitGeometries() {
        return new GlobalObjectsReader(EnumSet.of(GlobalObjects.Type.IMPLICIT_GEOMETRY));
    }

    public GlobalObjectsReader withTemplateAppearances(boolean withTemplateAppearances) {
        this.withTemplateAppearances = withTemplateAppearances;
        return this;
    }

    public GlobalObjects read(InputFile file, CityGMLContext context) throws ExecutionException {
        try {
            GlobalObjects globalObjects = new GlobalObjects();
            try (CityGMLReader reader = createReader(file, context)) {
                while (reader.hasNext()) {
                    AbstractFeature feature = reader.next();
                    if (feature instanceof Appearance) {
                        if (types.contains(GlobalObjects.Type.APPEARANCE)) {
                            globalObjects.add((Appearance) feature, reader.getName());
                        }
                    } else if (feature instanceof CityObjectGroup) {
                        if (types.contains(GlobalObjects.Type.CITY_OBJECT_GROUP)) {
                            globalObjects.add((CityObjectGroup) feature, reader.getName());
                        }
                    } else if (types.contains(GlobalObjects.Type.IMPLICIT_GEOMETRY)) {
                        GeometryInfo geometryInfo = feature.getGeometryInfo();
                        for (int lod : geometryInfo.getLods()) {
                            geometryInfo.getImplicitGeometries(lod).stream()
                                    .filter(ImplicitGeometryProperty::isSetInlineObject)
                                    .map(ImplicitGeometryProperty::getObject)
                                    .forEach(implicitGeometry ->
                                            globalObjects.add(implicitGeometry, lod, withTemplateAppearances));
                        }

                        geometryInfo.getNonLodImplicitGeometries().stream()
                                .filter(ImplicitGeometryProperty::isSetInlineObject)
                                .map(ImplicitGeometryProperty::getObject)
                                .forEach(implicitGeometry ->
                                        globalObjects.add(implicitGeometry, withTemplateAppearances));
                    }
                }
            }

            return globalObjects;
        } catch (CityGMLReadException e) {
            throw new ExecutionException("Failed to read global objects.", e);
        }
    }

    private CityGMLReader createReader(InputFile file, CityGMLContext context) throws ExecutionException {
        try {
            CityGMLInputFactory in = context.createCityGMLInputFactory()
                    .withChunking(ChunkOptions.defaults())
                    .withIdCreator(new IdCreator());

            CityGMLReader reader = in.createCityGMLReader(file.getFile());
            if (!types.contains(GlobalObjects.Type.IMPLICIT_GEOMETRY)) {
                Set<String> localNames = new HashSet<>();
                if (types.contains(GlobalObjects.Type.APPEARANCE)) {
                    localNames.add("Appearance");
                }

                if (types.contains(GlobalObjects.Type.CITY_OBJECT_GROUP)) {
                    localNames.add("CityObjectGroup");
                }

                return in.createFilteredCityGMLReader(reader, name -> localNames.contains(name.getLocalPart())
                        && CityGMLModules.isCityGMLNamespace(name.getNamespaceURI()));
            } else if (types.size() == 1 && types.contains(GlobalObjects.Type.IMPLICIT_GEOMETRY)) {
                return in.createFilteredCityGMLReader(reader, name -> !"Appearance".equals(name.getLocalPart())
                        || !CityGMLModules.isCityGMLNamespace(name.getNamespaceURI()));
            }

            return reader;
        } catch (CityGMLReadException e) {
            throw new ExecutionException("Failed to read global objects.", e);
        }
    }
}
