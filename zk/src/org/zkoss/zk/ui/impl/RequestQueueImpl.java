/* RequestQueueImpl.java

	Purpose:
		
	Description:
		
	History:
		Fri Jan 20 09:51:39     2006, Created by tomyeh

Copyright (C) 2006 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under LGPL Version 3.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.zk.ui.impl;

import java.util.Iterator;
import java.util.Collection;
import java.util.List;
import java.util.LinkedList;

import org.zkoss.lang.Objects;
import org.zkoss.util.logging.Log;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.ComponentNotFoundException;
import org.zkoss.zk.ui.sys.RequestQueue;
import org.zkoss.zk.au.AuRequest;

/**
 * An implementation of {@link RequestQueue} behaving as
 * a queue of {@link AuRequest}.
 * There is one queue for each desktop.
 *
 * <p>Implementation Note:
 * Unlike only of desktop members, this class must be thread-safe.
 *
 * @author tomyeh
 */
public class RequestQueueImpl implements RequestQueue {
	private static final Log log = Log.lookup(RequestQueueImpl.class);

	/** A list of pending {@link AuRequest}. */
	private final List _requests = new LinkedList();
	/** A list of request ID for performance measurement. */
	private List _pfreqIds;

	//-- RequestQueue --//
	public void addPerfRequestId(String requestId) {
		if (_pfreqIds == null)
			_pfreqIds = new LinkedList();
		_pfreqIds.add(requestId);
	}
	public Collection clearPerfRequestIds() {
		final List old = _pfreqIds;
		_pfreqIds = null;
		return old;
	}

	public boolean isEmpty() {
		while (!_requests.isEmpty()) {
			if (isObsolete((AuRequest)_requests.get(0)))
				_requests.remove(0);
			else
				return false;
		}
		return true;
	}
	private static boolean isObsolete(AuRequest request) {
		final Component comp = request.getComponent();
		if (comp != null) {
			final Desktop dt = comp.getDesktop();
			return dt != null && dt != request.getDesktop();
			//Since 3.5.0, we allow a component to process events even
			//if it is detached (due to implementation of EventQueue)
		}
		return false;
	}
	public AuRequest nextRequest() {
		while (!_requests.isEmpty()) {
			final AuRequest request = (AuRequest)_requests.remove(0);
			if (!isObsolete(request))
				return request;
		}
		return null;
	}
	public void addRequests(Collection requests) {
		for (Iterator it = requests.iterator(); it.hasNext();) {
			final AuRequest request = (AuRequest)it.next();
			try {
				request.activate();
				if (!isObsolete(request))
					addRequest(request);
			} catch (ComponentNotFoundException ex) { //ignore it
				//ignore it since a long request might remove a timer
				//while clients already queues onTimer generated by the timer
				if (log.debugable()) log.debug("Ignore request: "+ex.getMessage());
			}
		}
	}
	private void addRequest(AuRequest request) {
		//case 1, BUSY_IGNORE: Drop any existent ignorable requests
		//We don't need to iterate all because requests is added one-by-one
		//In other words, if any temporty request, it must be the last
		{
			int last = _requests.size() - 1;
			if (last < 0) { //optimize the most common case
				_requests.add(request);
				return;
			}

			final AuRequest req2 = (AuRequest)_requests.get(last);
			if ((req2.getOptions() & AuRequest.BUSY_IGNORE) != 0) {
				_requests.remove(last); //drop it
				if (last == 0) {
					_requests.add(request);
					return;
				}
			}
		}

		final String name = request.getCommand();
		final int opts = request.getOptions();

		//Since 3.0.2, redundant CTRL_GROUP is removed at the client

		//case 2, DUPLICATE_IGNORE: drop existent request if they are the same
		//as the arrival.
		if ((opts & AuRequest.DUPLICATE_IGNORE) != 0) {
			final String uuid = getUuid(request);
			for (Iterator it = _requests.iterator(); it.hasNext();) {
				final AuRequest req2 = (AuRequest)it.next();
				if (req2.getCommand().equals(name)
				&& Objects.equals(getUuid(req2), uuid)) {
					it.remove(); //drop req2
					break; //no need to iterate because impossible to have more
				}
			}

		//Case 3, REPEAT_IGNORE: drop existent if the immediate
		//following is the same
		} else if ((opts & AuRequest.REPEAT_IGNORE) != 0) {
			final int last = _requests.size() - 1;
			final AuRequest req2 = (AuRequest)_requests.get(last);
			if (req2.getCommand().equals(name)
			&& Objects.equals(getUuid(req2), getUuid(request))) {
				_requests.remove(last);
			}
		}

		_requests.add(request);
	}

	private String getUuid(AuRequest request) {
		final Component comp = request.getComponent();
		if (comp != null) return comp.getUuid();
		final Page page = request.getPage();
		if (page != null) return page.getUuid();
		return null;
	}
}
