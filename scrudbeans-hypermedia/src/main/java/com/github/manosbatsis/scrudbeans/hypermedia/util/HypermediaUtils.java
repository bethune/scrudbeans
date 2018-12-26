/**
 *
 * Restdude
 * -------------------------------------------------------------------
 *
 * Copyright © 2005 Manos Batsis (manosbatsis gmail)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.manosbatsis.scrudbeans.hypermedia.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import com.github.manosbatsis.scrudbeans.api.domain.Model;
import com.github.manosbatsis.scrudbeans.api.mdd.registry.FieldInfo;
import com.github.manosbatsis.scrudbeans.api.mdd.registry.ModelInfo;
import com.github.manosbatsis.scrudbeans.api.mdd.registry.ModelInfoRegistry;
import com.github.manosbatsis.scrudbeans.api.util.ParamsAwarePage;
import com.github.manosbatsis.scrudbeans.hypermedia.hateoas.ModelResource;
import com.github.manosbatsis.scrudbeans.hypermedia.hateoas.ModelResources;
import com.github.manosbatsis.scrudbeans.hypermedia.hateoas.PagedModelResources;
import com.github.manosbatsis.scrudbeans.hypermedia.jsonapi.JsonApiModelResourceDocument;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.BasicLinkBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Provides utilities for working with JSON API
 */
@Slf4j
public class HypermediaUtils {


	public static List<Link> buileHateoasLinks(@NonNull ParamsAwarePage page, @NonNull HttpServletRequest request, @NonNull String pageNumberParamName) {
		List<Link> links = new LinkedList<>();
		UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(request.getRequestURL() + "?" + request.getQueryString());
		// add first
		if (!page.isFirst()) {
			uriComponentsBuilder.replaceQueryParam(pageNumberParamName, 0);
			// create the link builder
			links.add(new UriComponentsBuilderAdapterLinkBuilder(uriComponentsBuilder).withRel("first"));
		}
		// add previous
		if (page.hasPrevious()) {
			uriComponentsBuilder.replaceQueryParam(pageNumberParamName, 1);
			// create the link builder
			links.add(new UriComponentsBuilderAdapterLinkBuilder(uriComponentsBuilder).withRel("previous"));
		}

		// add next
		if (page.hasNext()) {
			uriComponentsBuilder.replaceQueryParam(pageNumberParamName, page.getNumber() + 1);
			// create the link builder
			links.add(new UriComponentsBuilderAdapterLinkBuilder(uriComponentsBuilder).withRel("next"));
		}

		// add last
		if (!page.isLast()) {
			uriComponentsBuilder.replaceQueryParam(pageNumberParamName, page.getTotalPages() - 1);
			// create the link builder
			links.add(new UriComponentsBuilderAdapterLinkBuilder(uriComponentsBuilder).withRel("last"));
		}
		return links;
	}

	public static List<Link> buileHateoasLinks(@NonNull Model model, ModelInfo modelInfo) {
		List<Link> links = null;
		if (model.getId() != null && modelInfo != null) {

			links = new LinkedList<>();

			// add link to self
			links.add(BasicLinkBuilder.linkToCurrentMapping()
					.slash(modelInfo.getRequestMapping())
					.slash(model.getId()).withSelfRel());

			// add links to linkable relationships
			Set<String> relationshipFields = new HashSet<>();
			relationshipFields.addAll(modelInfo.getToOneFieldNames());
			relationshipFields.addAll(modelInfo.getToManyFieldNames());
			for (String fieldName : relationshipFields) {
				FieldInfo fieldInfo = modelInfo.getField(fieldName);

				if (fieldInfo.isLinkableResource()) {
					links.add(BasicLinkBuilder.linkToCurrentMapping()
							.slash(modelInfo.getRequestMapping())
							.slash(model.getId())
							.slash("relationships")
							.slash(fieldName).withRel(fieldName));
				}
			}
		}
		return links;
	}

	public static <RT extends Model<RID>, RID extends Serializable> ModelResource<RT> toHateoasResource(RT model, ModelInfo<RT, RID> modelInfo) {
		Class<RT> modelType = (modelInfo != null) ? modelInfo.getModelType() : (Class<RT>) model.getClass();
		ModelResource<RT> resource = new ModelResource<>(modelInfo.getUriComponent(), model);
		List<Link> links = HypermediaUtils.buileHateoasLinks(model, modelInfo);
		log.debug("toHateoasResource, model: {}, modelType: {}, modelInfo: {}", model, modelType, modelInfo);
		if (CollectionUtils.isNotEmpty(links)) {
			resource.add(links);
		}
		return resource;
	}

	/**
	 * Wrap the given models in a {@link Resources} and add {@link org.springframework.hateoas.Link}s
	 *
	 * @param models
	 */
	public static <RT extends Model> ModelResources<RT> toHateoasResources(@NonNull Iterable<RT> models, Class<RT> modelType, ModelInfoRegistry modelInfoRegistry) {
		log.debug("toHateoasResources");
		LinkedList<ModelResource<RT>> wrapped = new LinkedList<>();
		ModelInfo modelInfo;
		modelInfo = modelInfoRegistry.getEntryFor(modelType);
		for (RT model : models) {
			wrapped.add(new ModelResource<RT>(modelInfo.getUriComponent(), model));
		}
		ModelResources<RT> resources = new ModelResources<>(wrapped);
		return resources;
	}

	/**
	 * Wrap the given model in a JSON API Document
	 * @param model the model to wrap
	 * @return
	 */
	public static <RT extends Model<RID>, RID extends Serializable> JsonApiModelResourceDocument<RT, RID> toDocument(RT model, ModelInfo<RT, RID> modelInfo) {
		log.debug("toDocument");

		JsonApiModelResourceDocument<RT, RID> doc = new JsonApiModelBasedDocumentBuilder<RT, RID>(modelInfo.getUriComponent())
				.withData(model)
				.buildModelDocument();
		List<Link> tmp = HypermediaUtils.buileHateoasLinks(model, modelInfo);
		if (CollectionUtils.isNotEmpty(tmp)) {
			for (Link l : tmp) {
				doc.add(l.getRel(), l.getHref());
			}
		}
		return doc;
	}

	public static <M extends Model> PagedModelResources<M> toHateoasPagedResources(@NonNull ParamsAwarePage<M> page, @NonNull HttpServletRequest request, @NonNull String pageNumberParamName, ModelInfoRegistry modelInfoRegistry) {

		PagedResources.PageMetadata paginationInfo = new PagedResources.PageMetadata(page.getSize(), page.getNumber(), page.getTotalElements(), page.getTotalPages());
		List<Link> links = HypermediaUtils.buileHateoasLinks(page, request, pageNumberParamName);

		ArrayList<ModelResource<M>> wrapped = new ArrayList<>();
		ModelInfo modelInfo;
		for (M model : page.getContent()) {
			modelInfo = modelInfoRegistry.getEntryFor(model.getClass());
			wrapped.add(new ModelResource<M>(modelInfo.getUriComponent(), model));
		}

		return new PagedModelResources(wrapped, paginationInfo, page.getParameters(), links);

	}
}
