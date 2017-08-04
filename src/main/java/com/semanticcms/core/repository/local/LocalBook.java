/*
 * semanticcms-core-repository-local - SemanticCMS pages and associated resources produced by the local servlet container.
 * Copyright (C) 2014, 2015, 2016, 2017  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-core-repository-local.
 *
 * semanticcms-core-repository-local is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-core-repository-local is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-core-repository-local.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.repository.local;

import com.aoindustries.util.AoCollections;
import com.aoindustries.util.StringUtility;
import com.semanticcms.core.model.Author;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.Copyright;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.ParentRef;
import com.semanticcms.core.model.ResourceRef;
import com.semanticcms.core.repository.Book;
import com.semanticcms.core.resources.ResourceStore;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.servlet.ServletContext;

/**
 * A book where the pages in invoked locally.
 */
public class LocalBook extends Book {

	private static final String DEFAULT_DOMAIN = "localhost";

	private static final String PARAM_PREFIX = "param.";

	private final Set<ParentRef> unmodifiableParentRefs;
	private final PageRef contentRoot;
	private final Copyright copyright;
	private final Set<Author> unmodifiableAuthors;
	private final String title;
	private final boolean allowRobots;
	private final Map<String,String> unmodifiableParam;

	private final ResourceStore resourceStore;

	private static String getCanonicalBase(Properties bookProps) {
		String cb = StringUtility.nullIfEmpty(bookProps.getProperty("canonicalBase"));
		while(cb != null && cb.endsWith("/")) {
			cb = StringUtility.nullIfEmpty(cb.substring(0, cb.length() - 1));
		}
		return cb;
	}

	private static String getProperty(Properties bookProps, Set<Object> usedKeys, String key) {
		usedKeys.add(key);
		return bookProps.getProperty(key);
	}

	public LocalBook(
		ServletContext servletContext,
		String name,
		Collection<String> resourceDirectories,
		boolean allowRobots,
		Set<ParentRef> parentRefs,
		Properties bookProps // TODO: Load from resolved resourceStore?
	) {
		super(
			new BookRef(
				bookProps.getProperty("domain", DEFAULT_DOMAIN),
				name
			),
			getCanonicalBase(bookProps)
		);

		// Tracks each properties key used, will throw exception if any key exists in the properties file that is not used
		Set<Object> usedKeys = new HashSet<Object>(bookProps.size() * 4/3 + 1);

		// Mark as used
		getProperty(bookProps, usedKeys, "domain");
		getProperty(bookProps, usedKeys, "canonicalBase");

		this.unmodifiableParentRefs = AoCollections.optimalUnmodifiableSet(parentRefs);
		String copyrightRightsHolder = getProperty(bookProps, usedKeys, "copyright.rightsHolder");
		String copyrightRights = getProperty(bookProps, usedKeys, "copyright.rights");
		String copyrightDateCopyrighted = getProperty(bookProps, usedKeys, "copyright.dateCopyrighted");
		if(
			copyrightRightsHolder != null
			|| copyrightRights != null
			|| copyrightDateCopyrighted != null
		) {
			this.copyright = new Copyright(
				copyrightRightsHolder    != null ? copyrightRightsHolder    : "",
				copyrightRights          != null ? copyrightRights          : "",
				copyrightDateCopyrighted != null ? copyrightDateCopyrighted : ""
			);
		} else {
			this.copyright = null;
		}
		Set<Author> authors = new LinkedHashSet<Author>();
		for(int i=1; i<Integer.MAX_VALUE; i++) {
			String authorName = getProperty(bookProps, usedKeys, "author." + i + ".name");
			String authorHref = getProperty(bookProps, usedKeys, "author." + i + ".href");
			String authorDomain = getProperty(bookProps, usedKeys, "author." + i + ".domain");
			String authorBook = getProperty(bookProps, usedKeys, "author." + i + ".book");
			String authorPage = getProperty(bookProps, usedKeys, "author." + i + ".page");
			if(authorName==null && authorHref==null && authorDomain==null && authorBook==null && authorPage==null) break;
			// When domain provided, both book and page must also be provided.
			if(authorDomain != null) {
				if(authorBook == null) throw new IllegalArgumentException("When author. " + i + ".domain provided, both book and page must also be provided.");
			}
			// When book provided, page must also be provided.
			if(authorBook != null) {
				if(authorPage == null) throw new IllegalArgumentException("When author." + i + ".book provided, page must also be provided.");
			}
			if(authorPage != null) {
				// Default to this domain if nothing set
				if(authorDomain == null) authorDomain = this.bookRef.getDomain();
				// Default to this book if nothing set
				if(authorBook == null) authorBook = name;
			}
			// Name required when referencing an author outside this book
			if(authorName == null && authorBook != null) {
				assert authorDomain != null;
				if(
					!authorDomain.equals(this.bookRef.getDomain())
					|| !authorBook.equals(name)
				) {
					throw new IllegalStateException(name + ": Author name required when author is in a different book: " + authorPage);
				}
			}
			Author newAuthor = new Author(
				authorName,
				authorHref,
				authorDomain,
				authorBook,
				authorPage
			);
			if(!authors.add(newAuthor)) throw new IllegalStateException(name + ": Duplicate author: " + newAuthor);
		}
		this.unmodifiableAuthors = AoCollections.optimalUnmodifiableSet(authors);
		this.title = getProperty(bookProps, usedKeys, "title");
		this.allowRobots = allowRobots;
		Map<String,String> newParam = new LinkedHashMap<String,String>();
		@SuppressWarnings("unchecked")
		Enumeration<String> propertyNames = (Enumeration)bookProps.propertyNames();
		while(propertyNames.hasMoreElements()) {
			String propertyName = propertyNames.nextElement();
			if(propertyName.startsWith(PARAM_PREFIX)) {
				newParam.put(
					propertyName.substring(PARAM_PREFIX.length()),
					getProperty(bookProps, usedKeys, propertyName)
				);
			}
		}
		this.unmodifiableParam = AoCollections.optimalUnmodifiableMap(newParam);

		// Create the page refs once other aspects of the book have already been setup, since we'll be leaking "this"
		this.contentRoot = new PageRef(bookRef, getProperty(bookProps, usedKeys, "content.root"));

		// Make sure all keys used
		Set<Object> unusedKeys = new HashSet<Object>();
		for(Object key : bookProps.keySet()) {
			if(!usedKeys.contains(key)) unusedKeys.add(key);
		}
		if(!unusedKeys.isEmpty()) throw new IllegalStateException(name + ": Unused keys: " + unusedKeys);

		// Find the optional resource directory
		if(resourceDirectories == null || resourceDirectories.isEmpty()) {
			resourceStore = new ServletContextResourceStore(servletContext);
		} else {
			List<File> directories = new ArrayList<File>(resourceDirectories.size());
			for(String resourceDirectory : resourceDirectories) {
				File directory;
				if(resourceDirectory.startsWith("~/")) {
					directory = new File(System.getProperty("user.home"), resourceDirectory.substring(2));
				} else {
					directory = new File(resourceDirectory);
				}
				if(!directory.exists()) throw new FileNotFoundException("Directory not found: " + directory);
				if(!directory.isDirectory()) throw new IOException("Not a directory: " + directory);
				if(!directory.canRead()) throw new IOException("Unable to read directory: " + directory);
				directories.add(directory);
			}
			int numDirectories = directories.size();
			if(numDirectories == 1) {
				resourceStore = new DirectoryResourceStore(directories.get(0));
			} else {
				List<ResourceStore> resourceStores = new ArrayList<ResourceStore>(numDirectories);
				for(File directory : directories) {
					resourceStores.add(new DirectoryResourceStore(directory));
				}
				resourceStore = new UnionResourceStore(resourceStores);
			}
		}
	}

	@Override
	public boolean isAccessible() {
		return true;
	}

	@Override
	public ResourceStore getResourceStore() {
		return resourceStore;
	}

	/** Move to resource store
	private volatile File resourceFile;
	// TODO: Is this cached too long now that we have higher-level caching strategies?
	private volatile Boolean exists;
	*/

	/**
	 * the underlying file, only available when have access to the referenced book
	 * 
	 * @param requireBook when true, will always get a File object back
	 * @param requireFile when true, any File object returned will exist on the filesystem
	 *
	 * @return null if not access to book or File of resource path.
	 */
	/** Move to resource store
	public File getPageSourceFile(String path, boolean requireBook, boolean requireFile) {
		if(book == null) {
			if(requireBook) throw new IOException("Book not found: " + bookName);
			return null;
		} else {
			File rf = resourceFile;
			if(rf == null) {
				File cvsworkDirectory = book.getCvsworkDirectory();
				// Skip past first slash
				assert path.charAt(0) == '/';
				int start = 1;
				// Skip past any trailing slashes
				int end = path.length();
				while(end > start && path.charAt(end - 1) == '/') end--;
				String subPath = path.substring(start, end);
				// Combine paths
				rf = subPath.isEmpty() ? cvsworkDirectory : new File(cvsworkDirectory, subPath);
				// The canonical file must be in the cvswork directory
				String canonicalPath = rf.getCanonicalPath();
				if(
					!canonicalPath.startsWith(
						cvsworkDirectory.getCanonicalPath() + File.separatorChar
					)
				) {
					throw new SecurityException();
				}
				this.resourceFile = rf;
			}
			if(requireFile) {
				Boolean e = this.exists;
				if(e == null) {
					e = rf.exists();
					this.exists = e;
				}
				if(!e) throw new FileNotFoundException(rf.getPath());
			}
			return rf;
		}
	}
	*/

	/**
	 * TODO: Look for index.jspx, index.jsp.  In a per-book registered, extensible way?
	 */
	@Override
	public ResourceRef getPageSource(PageRef pageRef) {
		return null;
	}

	@Override
	public Set<ParentRef> getParentRefs() {
		return unmodifiableParentRefs;
	}

	@Override
	public PageRef getContentRoot() {
		return contentRoot;
	}

	@Override
	public Copyright getCopyright() {
		assert copyright == null || !copyright.isEmpty();
		return copyright;
	}

	@Override
	public Set<Author> getAuthors() {
		return unmodifiableAuthors;
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public boolean getAllowRobots() {
		return allowRobots;
	}

	@Override
	public Map<String,String> getParam() {
		return unmodifiableParam;
	}
}
