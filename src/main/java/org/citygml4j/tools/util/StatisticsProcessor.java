package org.citygml4j.tools.util;

import org.citygml4j.core.model.appearance.AbstractSurfaceData;
import org.citygml4j.core.model.appearance.AbstractTexture;
import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.appearance.X3DMaterial;
import org.citygml4j.core.model.core.AbstractGenericAttribute;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.util.CityGMLPatterns;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.log.Logger;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.Module;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.module.citygml.CoreModule;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.Envelope;
import org.xmlobjects.gml.model.geometry.GeometryProperty;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;

public class StatisticsProcessor {
    private final Statistics statistics;
    private final CityGMLContext context;
    private final StatisticsWalker statisticsWalker = new StatisticsWalker();
    private final Deque<FeatureInfo> featureInfos = new ArrayDeque<>();
    private final Matcher lodMatcher = CityGMLPatterns.LOD_FROM_PROPERTY_NAME.matcher("");

    private boolean computeEnvelope;
    private boolean onlyTopLevelFeatures;
    private boolean generateObjectHierarchy;
    private Map<String, AbstractGeometry> templates;

    private StatisticsProcessor(Statistics statistics, CityGMLContext context) {
        this.statistics = statistics;
        this.context = context;
    }

    public static StatisticsProcessor of(Statistics statistics, CityGMLContext context) {
        return new StatisticsProcessor(statistics, context);
    }

    public StatisticsProcessor computeEnvelope(boolean computeEnvelope) {
        this.computeEnvelope = computeEnvelope;
        return this;
    }

    public StatisticsProcessor onlyTopLevelFeatures(boolean onlyTopLevelFeatures) {
        this.onlyTopLevelFeatures = onlyTopLevelFeatures;
        return this;
    }

    public StatisticsProcessor generateObjectHierarchy(boolean generateObjectHierarchy) {
        this.generateObjectHierarchy = generateObjectHierarchy;
        return this;
    }

    public void process(QName element, String prefix, boolean isTopLevel, int depth, Statistics statistics) {
        statistics.addVersion(CityGMLModules.getCityGMLVersion(element.getNamespaceURI()));

        Module module = CityGMLModules.getModuleFor(element.getNamespaceURI());
        if (module instanceof CoreModule
                && !statistics.hasObjects(Statistics.ObjectType.FEATURE)
                && "CityModel".equals(element.getLocalPart())) {
            return;
        }

        if (!statistics.getExtent().isValid()) {
            computeEnvelope = true;
        }

        if (module != null) {
            prefix = module.getNamespacePrefix();
        } else {
            prefix = XMLConstants.DEFAULT_NS_PREFIX.equals(prefix) ? "ade" : prefix;
        }

        statistics.addModule(prefix, element.getNamespaceURI());
        if (isTopLevel || !onlyTopLevelFeatures) {
            addObject(element, prefix, statistics::addFeature);

            if (generateObjectHierarchy) {
                featureInfos.push(new FeatureInfo(prefix + ":" + element.getLocalPart(), depth));
                List<String> hierarchy = new ArrayList<>();
                featureInfos.descendingIterator()
                        .forEachRemaining(featureInfo -> hierarchy.add(featureInfo.getName()));
                statistics.addFeatureHierarchy(hierarchy);
            }
        }
    }

    public void process(QName element, Appearance appearance, boolean isTopLevel) {
        if (appearance != null) {
            if (isTopLevel) {
                statistics.setHasGlobalAppearances(true);
            }

            addObjectAndModule(element, getPrefix(element.getNamespaceURI(), "app"), statistics::addAppearance);
            statistics.addTheme(appearance.getTheme());
            appearance.accept(statisticsWalker);
        }
    }

    public void process(QName element, AbstractGeometry geometry, QName parent) {
        if (geometry != null) {
            addObjectAndModule(element, getPrefix(element.getNamespaceURI(), "gml"), statistics::addGeometry);
            getLodFromPropertyName(parent);

            if (computeEnvelope) {
                statistics.getExtent().include(geometry.computeEnvelope());
            }

            geometry.accept(statisticsWalker);
        }
    }

    public void process(QName element, ImplicitGeometry implicitGeometry, QName parent) throws ExecutionException {
        if (implicitGeometry != null) {
            addObjectAndModule(element, getPrefix(element.getNamespaceURI(), "core"), statistics::addGeometry);
            getLodFromPropertyName(parent);

            if (computeEnvelope) {
                GeometryProperty<?> property = implicitGeometry.getRelativeGeometry();
                if (property != null
                        && property.getObject() == null
                        && property.getHref() != null) {
                    if (templates == null) {
                        lazyLoadTemplates();
                    }

                    AbstractGeometry template = templates.get(CityObjects.getIdFromReference(property.getHref()));
                    if (template != null) {
                        property.setReferencedObjectIfValid(template);
                    }
                }

                statistics.getExtent().include(implicitGeometry.computeEnvelope());
            }

            statistics.setHasImplicitGeometries(true);
            implicitGeometry.accept(statisticsWalker);
        }
    }

    public void process(AbstractGenericAttribute<?> genericAttribute) {
        if (genericAttribute != null) {
            statistics.addGenericAttribute(genericAttribute.getName(), genericAttribute.getClass().getSimpleName());
        }
    }

    public void process(Envelope envelope, Statistics statistics) {
        if (envelope != null) {
            statistics.addReferenceSystem(envelope.getSrsName());
            if (!computeEnvelope && !statistics.hasObjects(Statistics.ObjectType.FEATURE)) {
                statistics.getExtent().include(envelope);
            }
        }
    }

    public void updateFeatureHierarchy(int depth) {
        if (generateObjectHierarchy
                && !featureInfos.isEmpty()
                && featureInfos.peek().getDepth() == depth + 1) {
            featureInfos.pop();
        }
    }

    private void addObjectAndModule(QName element, String prefix, Consumer<String> consumer) {
        statistics.addModule(prefix, element.getNamespaceURI());
        addObject(element, prefix, consumer);
    }

    private void addObject(QName element, String prefix, Consumer<String> consumer) {
        consumer.accept(prefix + ":" + element.getLocalPart());
    }

    private void getLodFromPropertyName(QName property) {
        if (property != null) {
            lodMatcher.reset(property.getLocalPart());
            if (lodMatcher.matches()) {
                statistics.addLod(Integer.parseInt(lodMatcher.group(1)));
            }
        }
    }

    private String getPrefix(String namespaceURI, String defaultPrefix) {
        Module module = CityGMLModules.getModuleFor(namespaceURI);
        return module != null ? module.getNamespacePrefix() : defaultPrefix;
    }

    private void lazyLoadTemplates() throws ExecutionException {
        Logger.getInstance().debug("Reading implicit geometries from input file.");
        templates = new HashMap<>();
        List<ImplicitGeometry> implicitGeometries = GlobalObjectsReader.onlyImplicitGeometries()
                .read(statistics.getFile(), context)
                .getImplicitGeometries();

        for (ImplicitGeometry implicitGeometry : implicitGeometries) {
            if (implicitGeometry.getRelativeGeometry() != null
                    && implicitGeometry.getRelativeGeometry().getObject() != null
                    && implicitGeometry.getRelativeGeometry().getObject().getId() != null) {
                AbstractGeometry template = implicitGeometry.getRelativeGeometry().getObject();
                templates.put(template.getId(), template);
            }
        }
    }

    private class StatisticsWalker extends ObjectWalker {

        @Override
        public void visit(AbstractGeometry geometry) {
            statistics.addReferenceSystem(geometry.getSrsName());
            super.visit(geometry);
        }

        @Override
        public void visit(AbstractSurfaceData surfaceData) {
            statistics.addAppearance("app:" + surfaceData.getClass().getSimpleName());
        }

        @Override
        public void visit(AbstractTexture texture) {
            statistics.setHasTextures(true);
            super.visit(texture);
        }

        @Override
        public void visit(X3DMaterial x3dMaterial) {
            statistics.setHasMaterials(true);
            super.visit(x3dMaterial);
        }
    }

    private static class FeatureInfo {
        private final String name;
        private final int depth;

        FeatureInfo(String name, int depth) {
            this.name = name;
            this.depth = depth;
        }

        public String getName() {
            return name;
        }

        public int getDepth() {
            return depth;
        }
    }
}