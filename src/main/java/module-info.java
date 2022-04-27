/*
 * semanticcms-core-pages-servlet - SemanticCMS pages produced by the local servlet container.
 * Copyright (C) 2021, 2022  AO Industries, Inc.
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
module com.semanticcms.core.pages.servlet {
  exports com.semanticcms.core.pages.servlet;
  // Direct
  requires com.aoapps.hodgepodge; // <groupId>com.aoapps</groupId><artifactId>ao-hodgepodge</artifactId>
  requires com.aoapps.net.types; // <groupId>com.aoapps</groupId><artifactId>ao-net-types</artifactId>
  requires com.aoapps.servlet.util; // <groupId>com.aoapps</groupId><artifactId>ao-servlet-util</artifactId>
  requires javax.servlet.api; // <groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId>
  requires com.semanticcms.core.pages.local; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-pages-local</artifactId>
  // Transitive
  requires com.aoapps.lang; // <groupId>com.aoapps</groupId><artifactId>ao-lang</artifactId>
  requires com.semanticcms.core.pages; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-pages</artifactId>
} // TODO: Avoiding rewrite-maven-plugin-4.22.2 truncation
