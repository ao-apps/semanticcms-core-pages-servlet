/*
 * semanticcms-core-pages-servlet - SemanticCMS pages produced by the local servlet container.
 * Copyright (C) 2017, 2018, 2019, 2020, 2021, 2022, 2025  AO Industries, Inc.
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
 * along with semanticcms-core-pages-servlet.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.semanticcms.core.pages.servlet;

import com.aoapps.hodgepodge.util.Tuple2;
import com.aoapps.net.Path;
import com.aoapps.servlet.attribute.ScopeEE;
import com.semanticcms.core.pages.local.LocalPageRepository;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * Accesses pages from servlets in the local {@link ServletContext}.
 */
public class ServletPageRepository extends LocalPageRepository {

  /**
   * Initializes the Servlet page repository during {@linkplain ServletContextListener application start-up}.
   */
  @WebListener("Initializes the Servlet page repository during application start-up.")
  public static class Initializer implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
      getInstances(event.getServletContext());
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
      // Do nothing
    }
  }

  private static final ScopeEE.Application.Attribute<ConcurrentMap<Path, ServletPageRepository>> INSTANCES_APPLICATION_ATTRIBUTE =
      ScopeEE.APPLICATION.attribute(ServletPageRepository.class.getName() + ".instances");

  private static ConcurrentMap<Path, ServletPageRepository> getInstances(ServletContext servletContext) {
    return INSTANCES_APPLICATION_ATTRIBUTE.context(servletContext).computeIfAbsent(name -> new ConcurrentHashMap<>());
  }

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
      if (!"/".equals(pathStr) && pathStr.endsWith("/")) {
        path = path.prefix(pathStr.length() - 1);
      }
    }

    ConcurrentMap<Path, ServletPageRepository> instances = getInstances(servletContext);
    ServletPageRepository repository = instances.get(path);
    if (repository == null) {
      repository = new ServletPageRepository(servletContext, path);
      ServletPageRepository existing = instances.putIfAbsent(path, repository);
      if (existing != null) {
        repository = existing;
      }
    }
    return repository;
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
  protected Tuple2<String, RequestDispatcher> getRequestDispatcher(Path path) throws IOException {
    String pathStr = path.toString();
    String servletPath;
    int prefixLen = prefix.length();
    if (prefixLen == 0) {
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
    if (dispatcher != null) {
      return new Tuple2<>(servletPath, dispatcher);
    } else {
      return null;
    }
  }
}
