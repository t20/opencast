/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.opencastproject.event.comment.persistence;

import static org.opencastproject.util.persistencefn.Queries.persistOrUpdate;

import org.opencastproject.event.comment.EventComment;
import org.opencastproject.index.IndexProducer;
import org.opencastproject.message.broker.api.MessageReceiver;
import org.opencastproject.message.broker.api.MessageSender;
import org.opencastproject.message.broker.api.comments.CommentItem;
import org.opencastproject.message.broker.api.index.AbstractIndexProducer;
import org.opencastproject.message.broker.api.index.IndexRecreateObject;
import org.opencastproject.message.broker.api.index.IndexRecreateObject.Service;
import org.opencastproject.security.api.DefaultOrganization;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.security.util.SecurityUtil;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.data.Effect0;
import org.opencastproject.util.data.Function;
import org.opencastproject.util.data.Monadics;
import org.opencastproject.util.persistencefn.PersistenceEnv;
import org.opencastproject.util.persistencefn.PersistenceEnvs;

import com.entwinemedia.fn.Fn;
import com.entwinemedia.fn.Stream;

import org.apache.commons.lang3.text.WordUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.osgi.framework.ServiceException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.persistence.Query;

/**
 * Implements permanent storage for event comments.
 */
public class EventCommentDatabaseServiceImpl extends AbstractIndexProducer implements EventCommentDatabaseService {
  /** Logging utilities */
  private static final Logger logger = LoggerFactory.getLogger(EventCommentDatabaseServiceImpl.class);

  public static final String PERSISTENCE_UNIT = "org.opencastproject.event.comment";

  /** Factory used to create {@link EntityManager}s for transactions */
  private EntityManagerFactory emf;

  /** Persistence environment */
  private PersistenceEnv env;

  /** The security service used to retrieve organizations. */
  private OrganizationDirectoryService organizationDirectoryService;

  /** The security service used to run the security context with. */
  private SecurityService securityService;

  /** The user directory service */
  private UserDirectoryService userDirectoryService;

  /** The message broker sender service */
  private MessageSender messageSender;

  /** The message broker receiver service */
  private MessageReceiver messageReceiver;

  /** The component context this bundle is running in. */
  private ComponentContext cc;

  /** OSGi component activation callback */
  public void activate(ComponentContext cc) {
    logger.info("Activating persistence manager for event comments");
    this.cc = cc;
    super.activate();
  }

  /** OSGi DI */
  public void setEntityManagerFactory(EntityManagerFactory emf) {
    this.emf = emf;
    this.env = PersistenceEnvs.mk(emf);
  }

  /**
   * OSGi callback to set the security context to run with.
   *
   * @param securityService
   *          The security service
   */
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  /**
   * OSGi callback to set the user directory service.
   *
   * @param userDirectoryService
   *          the user directory service
   */
  public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
    this.userDirectoryService = userDirectoryService;
  }

  /**
   * OSGi callback to set the organization directory service.
   *
   * @param userDirectoryService
   *          the user directory service
   */
  public void setOrganizationDirectoryService(OrganizationDirectoryService organizationDirectoryService) {
    this.organizationDirectoryService = organizationDirectoryService;
  }

  /**
   * OSGi callback to set the message sender.
   *
   * @param messageSender
   *          the message sender
   */
  public void setMessageSender(MessageSender messageSender) {
    this.messageSender = messageSender;
  }

  /**
   * OSGi callback to set the message receiver.
   *
   * @param messageReceiver
   *          the message receiver
   */
  public void setMessageReceiver(MessageReceiver messageReceiver) {
    this.messageReceiver = messageReceiver;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<String> getReasons() throws EventCommentDatabaseException {
    EntityManager em = emf.createEntityManager();
    try {
      Query q = em.createNamedQuery("EventComment.findReasons");
      q.setParameter("org", securityService.getOrganization().getId());
      return q.getResultList();
    } catch (Exception e) {
      logger.error("Could not get reasons: {}", ExceptionUtils.getStackTrace(e));
      throw new EventCommentDatabaseException(e);
    } finally {
      if (em != null)
        em.close();
    }
  }

  @Override
  public EventComment getComment(long commentId) throws NotFoundException, EventCommentDatabaseException {
    EntityManager em = emf.createEntityManager();
    try {
      EventCommentDto event = getEventComment(commentId, em);
      if (event == null)
        throw new NotFoundException("Event comment with ID " + commentId + " does not exist");

      return event.toComment(userDirectoryService);
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Could not get event comment {}: {}", commentId, ExceptionUtils.getStackTrace(e));
      throw new EventCommentDatabaseException(e);
    } finally {
      if (em != null)
        em.close();
    }
  }

  @Override
  public void deleteComment(long commentId) throws NotFoundException, EventCommentDatabaseException {
    EntityManager em = emf.createEntityManager();
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      EventCommentDto event = getEventComment(commentId, em);
      if (event == null)
        throw new NotFoundException("Event comment with ID " + commentId + " does not exist");

      em.remove(event);
      tx.commit();
      sendMessageUpdate(event.getEventId());
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Could not delete event comment: {}", ExceptionUtils.getStackTrace(e));
      if (tx.isActive())
        tx.rollback();

      throw new EventCommentDatabaseException(e);
    } finally {
      if (em != null)
        em.close();
    }
  }

  @Override
  public EventComment updateComment(EventComment comment) throws EventCommentDatabaseException {
    final EventCommentDto commentDto = EventCommentDto.from(comment);
    final EventComment updatedComment = env.tx(persistOrUpdate(commentDto)).toComment(userDirectoryService);
    sendMessageUpdate(updatedComment.getEventId());
    return updatedComment;
  }

  /**
   * Gets an event comment, using the current organizational context.
   *
   * @param commentId
   *          the comment identifier
   * @param em
   *          an open entity manager
   *
   * @return the event comment entity, or null if not found
   */
  private EventCommentDto getEventComment(long commentId, EntityManager em) {
    Query q = em.createNamedQuery("EventComment.findByCommentId");
    q.setParameter("commentId", commentId);
    try {
      return (EventCommentDto) q.getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<EventComment> getComments(String eventId) throws EventCommentDatabaseException {
    EntityManager em = emf.createEntityManager();
    try {
      Query q = em.createNamedQuery("EventComment.findByEvent");
      q.setParameter("eventId", eventId);
      q.setParameter("org", securityService.getOrganization().getId());

      List<EventComment> comments = Monadics.mlist(q.getResultList())
              .map(new Function<EventCommentDto, EventComment>() {
                @Override
                public EventComment apply(EventCommentDto a) {
                  return a.toComment(userDirectoryService);
                }
              }).sort(new Comparator<EventComment>() {
                @Override
                public int compare(EventComment c1, EventComment c2) {
                  boolean v1 = c1.isResolvedStatus();
                  boolean v2 = c2.isResolvedStatus();
                  return (v1 ^ v2) ? ((v1 ^ false) ? 1 : -1) : 0;
                }
              }).value();
      return new ArrayList<EventComment>(comments);
    } catch (Exception e) {
      logger.error("Could not retreive comments for event {}: {}", eventId, ExceptionUtils.getStackTrace(e));
      throw new EventCommentDatabaseException(e);
    } finally {
      if (em != null)
        em.close();
    }
  }

  @SuppressWarnings("unchecked")
  public Iterator<EventCommentDto> getComments() throws EventCommentDatabaseException {
    EntityManager em = emf.createEntityManager();
    try {
      Query q = em.createNamedQuery("EventComment.findAll");
      return new ArrayList<EventCommentDto>(q.getResultList()).iterator();
    } catch (Exception e) {
      logger.error("Could not retreive event comments: {}", ExceptionUtils.getStackTrace(e));
      throw new EventCommentDatabaseException(e);
    } finally {
      if (em != null)
        em.close();
    }
  }

  public int countComments() throws EventCommentDatabaseException {
    EntityManager em = emf.createEntityManager();
    Query query = em.createNamedQuery("EventComment.countAll");
    try {
      Number total = (Number) query.getSingleResult();
      return total.intValue();
    } catch (Exception e) {
      logger.error("Could not find the number of comments.", e);
      throw new EventCommentDatabaseException(e);
    } finally {
      em.close();
    }
  }

  private void sendMessageUpdate(String eventId) throws EventCommentDatabaseException {
    List<EventComment> comments = getComments(eventId);
    CommentItem update;
    if (comments.size() > 0) {
      int openCommentSize = Stream.$(comments).filter(filterOpenComments).toList().size();
      update = CommentItem.update(eventId, true, openCommentSize > 0 ? true : false);
    } else {
      update = CommentItem.update(eventId, false, false);
    }
    messageSender.sendObjectMessage(CommentItem.COMMENT_QUEUE, MessageSender.DestinationType.Queue, update);
  }

  private static final Fn<EventComment, Boolean> filterOpenComments = new Fn<EventComment, Boolean>() {
    @Override
    public Boolean ap(EventComment comment) {
      return !comment.isResolvedStatus();
    }
  };

  @Override
  public void repopulate(final String indexName) throws Exception {
    final String destinationId = CommentItem.COMMENT_QUEUE_PREFIX + WordUtils.capitalize(indexName);
    try {
      final int total = countComments();
      final int[] current = new int[1];
      current[0] = 1;
      logger.info("Re-populating index '{}' with comments for events. There are {} events with comments to add",
              indexName, total);
      for (Iterator<EventCommentDto> i = getComments(); i.hasNext();) {
        final EventCommentDto comment = i.next();
        Organization organization = organizationDirectoryService.getOrganization(comment.getOrganization());
        SecurityUtil.runAs(securityService, organization, SecurityUtil.createSystemUser(cc, organization),
                new Effect0() {
          @Override
          protected void run() {
            messageSender.sendObjectMessage(destinationId, MessageSender.DestinationType.Queue,
                    CommentItem.update(comment.getEventId(), true, !comment.isResolvedStatus()));
            messageSender.sendObjectMessage(IndexProducer.RESPONSE_QUEUE, MessageSender.DestinationType.Queue,
                    IndexRecreateObject.update(indexName, IndexRecreateObject.Service.Comments, total,
                            current[0]));
            current[0] += 1;
          }
        });
      }
    } catch (Exception e) {
      logger.warn("Unable to index event comments: {}", e);
      throw new ServiceException(e.getMessage());
    }

    Organization organization = new DefaultOrganization();
    SecurityUtil.runAs(securityService, organization, SecurityUtil.createSystemUser(cc, organization), new Effect0() {
      @Override
      protected void run() {
        messageSender.sendObjectMessage(destinationId, MessageSender.DestinationType.Queue,
                IndexRecreateObject.end(indexName, IndexRecreateObject.Service.Comments));
      }
    });
  }

  @Override
  public MessageReceiver getMessageReceiver() {
    return messageReceiver;
  }

  @Override
  public Service getService() {
    return Service.Comments;
  }

  @Override
  public String getClassName() {
    return EventCommentDatabaseServiceImpl.class.getName();
  }

}
