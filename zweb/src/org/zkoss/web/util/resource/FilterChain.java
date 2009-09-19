/* FilterChain.java

	Purpose:
		
	Description:
		
	History:
		Mon Sep 22 18:29:26     2008, Created by tomyeh

Copyright (C) 2008 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under LGPL Version 3.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.web.util.resource;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A {@link FilterChain} is an object provided by ZK to the developer
 * giving a view into the invocation chain of a filtered request.
 * Filters use {@link FilterChain} to invoke the next filter
 * ({@link Filter}) in the chain,
 * or if the calling filter is the last filter in the chain,
 * to invoke {@link Extendlet} at the end of the chain. 
 *
 * @author tomyeh
 * @since 3.5.1
 */
public interface FilterChain {
	/**  Causes the next filter {@link Filter} in the chain to be invoked,
	 * or if the calling filter is the last filter in the chain,
	 * causes {@link Extendlet} at the end of the chain to be invoked.
	 *
	 * @param request the request (never null).
	 * @param response the response (never null).
	 */
	public void doFilter(HttpServletRequest request,
	HttpServletResponse response)
	throws ServletException, IOException;
}
