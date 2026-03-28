/*
 * MoreMcmeta is a Minecraft mod expanding texture configuration capabilities.
 * Copyright (C) 2023 soir20
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package committee.nova.moremcmeta.jsonparserplugin;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import committee.nova.moremcmeta.api.client.metadata.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.logging.log4j.LogManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Reads JSON metadata from a .moremcmeta file.
 * @author soir20
 */
public final class JsonMetadataParser implements MetadataParser {

    @Override
    public Map<ResourceLocation, MetadataView> parse(ResourceLocation metadataLocation, InputStream metadataStream,
                                                     ResourceRepository resourceRepository)
            throws InvalidMetadataException {
        ResourceLocation textureLocation = ResourceLocation.fromNamespaceAndPath(
                metadataLocation.getNamespace(),
                metadataLocation.getPath().substring(0, metadataLocation.getPath().lastIndexOf('.'))
        );

        return ImmutableMap.of(textureLocation, parse(metadataStream));
    }

    @Override
    public Map<? extends RootResourceName, ? extends Map<? extends RootResourceName, ? extends MetadataView>> parse(
            ResourceRepository.Pack pack) {
        RootResourceName metadataName = new RootResourceName("pack.png." + ModConstants.MOREMCMETA_EXTENSION);
        ResourceLocation metadataLocation = pack.locateRootResource(metadataName);

        Optional<InputStream> metadataOptional = pack.resource(metadataLocation);
        if (metadataOptional.isPresent()) {
            try {
                return ImmutableMap.of(
                        metadataName,
                        ImmutableMap.of(
                                new RootResourceName("pack.png"),
                                parse(metadataOptional.get())
                        )
                );
            } catch (InvalidMetadataException err) {
                LogManager.getLogger().error("{} is invalid: {}", metadataName, err);
            }
        }

        return ImmutableMap.of();
    }

    /**
     * Parses a {@link InputStream} into a {@link MetadataView}.
     * @param metadataStream    stream of bytes representing JSON metadata
     * @return metadata view from the stream of bytes
     * @throws InvalidMetadataException if the metadata is not valid JSON
     */
    private MetadataView parse(InputStream metadataStream) throws InvalidMetadataException {
        BufferedReader bufferedReader = null;
        MetadataView metadata;

        try {
            bufferedReader = new BufferedReader(new InputStreamReader(metadataStream, StandardCharsets.UTF_8));
            JsonObject metadataObject = GsonHelper.parse(bufferedReader);

            // Create another root object to reuse its integer parsing code
            MetadataView unsortedRoot = new JsonMetadataView(metadataObject, String::compareTo);

            metadata = new JsonMetadataView(metadataObject,
                    (section1, section2) -> compareSections(unsortedRoot, section1, section2)
            );
        } catch (JsonParseException parseError) {
            throw new InvalidMetadataException("Metadata is not valid JSON");
        } finally {
            IOUtils.closeQuietly(bufferedReader);
        }

        return metadata;
    }

    /**
     * <p> Compares two section names at the topmost metadata level to
     * determine plugin application order. Compares the sections
     * based on priority and then based on lexicographical ordering
     * of their names.
     *
     * <p> If section1 should precede section2, a negative integer is returned.
     * <p> If section2 should precede section1, a positive integer is returned.
     * <p> If section1 and section2 are identical, then zero is returned.
     * @param root          top-level view of the metadata
     * @param section1      first section to compare
     * @param section2      second section to compare
     * @return a negative integer if section1 precedes section2, a positive
     *         integer if section2 precedes section1, or zero if they are
     *         the same
     */
    private int compareSections(MetadataView root, String section1, String section2) {
        MetadataView view1 = root.subView(section1).orElseThrow();
        MetadataView view2 = root.subView(section2).orElseThrow();

        final String PRIORITY_KEY = "layer";
        int layerDiff = view1.integerValue(PRIORITY_KEY).orElse(0) - view2.integerValue(PRIORITY_KEY).orElse(0);

        if (layerDiff != 0) {
            return layerDiff;
        }

        return section1.compareTo(section2);
    }

}
