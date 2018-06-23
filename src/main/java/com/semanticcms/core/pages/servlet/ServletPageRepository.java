/*
 * semanticcms-core-pages-servlet - SemanticCMS pages produced by the local servlet container.
 * Copyright (C) 2017, 2018  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-core-pages-servlet.
 *
 * semanticcms-core-pages-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-core-pages-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-core-pages-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.pages.servlet;

import com.aoindustries.net.Path;
import com.aoindustries.util.Tuple2;
import com.aoindustries.validation.ValidationException;
import com.semanticcms.core.pages.local.LocalPageRepository;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;

/**
 * Accesses pages from servlets in the local {@link ServletContext}.
 */
public class ServletPageRepository extends LocalPageRepository {

	private static final String INSTANCES_SERVLET_CONTEXT_KEY = ServletPageRepository.class.getName() + ".instances";

	/**
	 * Gets the servlet repository for the given context and path.
	 * Only one {@link ServletPageRepository} is created per unique context and path.
	 *
	 * @param  path  Must be a {@link Path valid path}.
	 *               Any trailing slash "/" will be stripped.
	 */
	public static ServletPageRepository getInstance(ServletContext servletContext, Path path) {
		// Strip trailing '/' to normalize
		{
			String pathStr = path.toString();
			if(!pathStr.equals("/") && pathStr.endsWith("/")) {
				path = path.prefix(pathStr.length() - 1);
			}
		}

		Map<Path,ServletPageRepository> instances;
		synchronized(servletContext) {
			@SuppressWarnings("unchecked")
			Map<Path,ServletPageRepository> map = (Map<Path,ServletPageRepository>)servletContext.getAttribute(INSTANCES_SERVLET_CONTEXT_KEY);
			if(map == null) {
				map = new HashMap<Path,ServletPageRepository>();
				servletContext.setAttribute(INSTANCES_SERVLET_CONTEXT_KEY, map);
			}
			instances = map;
		}
		synchronized(instances) {
			ServletPageRepository repository = instances.get(path);
			if(repository == null) {
				repository = new ServletPageRepository(servletContext, path);
				instances.put(path, repository);
			}
			return repository;
		}
	}

	private ServletPageRepository(ServletContext servletContext, Path path) {
		super(servletContext, path);
	}

	@Override
	public String toString() {
		return "servlet:" + prefix;
	}

	/**
	 * TODO: Can we use an annotation on servlets to ensure to not invoke non-page servlets?
	 * TODO: Or a central registry of expected servlets?  We don't want external requests being
	 *       mapped onto arbitrary servlets willy-nilly.
	 */
	@Override
	protected Tuple2<String,RequestDispatcher> getRequestDispatcher(Path path) throws IOException {
		String pathStr = path.toString();
		String servletPath;
		int prefixLen = prefix.length();
		if(prefixLen == 0) {
			servletPath = pathStr;
		} else {
			int len =
				prefixLen
				+ pathStr.length();
			servletPath =
				new StringBuilder(len)
				.append(prefix)
				.append(pathStr)
				.toString();
			assert servletPath.length() == len;
		}
		RequestDispatcher dispatcher = servletContext.getRequestDispatcher(servletPath);
		if(dispatcher != null) {
			return new Tuple2<String, RequestDispatcher>(servletPath, dispatcher);
		} else {
			return null;
		}
	}
}
