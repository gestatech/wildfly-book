package it.vige.businesscomponents.persistence;

import static it.vige.businesscomponents.persistence.Default.createJavaArchive;
import static java.util.logging.Logger.getLogger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class CommonTestCase {

	private static final Logger logger = getLogger(CommonTestCase.class.getName());

	@PersistenceContext
	private EntityManager entityManager;

	@Deployment
	public static JavaArchive createJavaDeployment() {
		JavaArchive jar = createJavaArchive("common-test.jar", Forum.class.getPackage());
		jar.addAsResource(new FileAsset(new File("src/main/resources/forums.import.sql")), "forums.import.sql");
		return jar;
	}

	/**
	 * Tests annotation literals in a jar archive
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void testSqlScript() {
		logger.info("starting common persistence test");
		assertNotNull("The injected entity manager", entityManager);
		List<Category> categories = entityManager.createNamedQuery("findCategories").getResultList();
		assertEquals("one category created by the sql script", 1, categories.size());
		List<Forum> forums = entityManager.createNamedQuery("findForumsByCategoryId")
				.setParameter("categoryId", categories.get(0)).getResultList();
		assertEquals("two forums created by the sql script", 2, forums.size());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testQuery() {
		Query query = entityManager
				.createQuery("select f from Forum f left outer join fetch f.topics where f.id = :forumId");
		query.setParameter("forumId", 1);
		List<Forum> forumList = query.getResultList();
		assertEquals("simple query ok", 1, forumList.size());
		query = entityManager.createNamedQuery("findForumByIdFetchTopics");
		query.setParameter("forumId", 1);
		forumList = query.getResultList();
		assertEquals("named query ok", 1, forumList.size());
	}
}
