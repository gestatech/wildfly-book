package it.vige.businesscomponents.transactions;

import static it.vige.businesscomponents.transactions.concurrent.ConcurrentStatus.latch;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Logger.getLogger;
import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.jboss.shrinkwrap.api.asset.EmptyAsset.INSTANCE;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import it.vige.businesscomponents.transactions.concurrent.MyCallableTask;
import it.vige.businesscomponents.transactions.concurrent.QueryReadAccount;
import it.vige.businesscomponents.transactions.concurrent.QueryWriteAccount;
import it.vige.businesscomponents.transactions.concurrent.ReadAccount;
import it.vige.businesscomponents.transactions.concurrent.WriteAccount;

@RunWith(Arquillian.class)
public class ViolationsTestCase {

	private static final Logger logger = getLogger(ViolationsTestCase.class.getName());

	@Resource(name = "DefaultManagedExecutorService")
	private ManagedExecutorService defaultExecutor;

	@Inject
	private ReadAccount readAccount;

	@Inject
	private WriteAccount writeAccount;

	@Inject
	private QueryReadAccount queryReadAccount;

	@Inject
	private QueryWriteAccount queryWriteAccount;

	@Deployment
	public static JavaArchive createEJBDeployment() {
		final JavaArchive jar = create(JavaArchive.class, "violations-test.jar");
		jar.addPackage(MyCallableTask.class.getPackage());
		jar.addClass(Account.class);
		jar.addAsManifestResource(new FileAsset(new File("src/test/resources/META-INF/persistence-test.xml")),
				"persistence.xml");
		jar.addAsResource(new FileAsset(new File("src/test/resources/store.import.sql")), "store.import.sql");
		jar.addAsManifestResource(INSTANCE, "beans.xml");
		return jar;
	}

	@Test
	public void testDirtyRead() throws Exception {
		logger.info("start dirty read");
		readAccount.setAccountNumber(123);
		readAccount.setFirstWaitTime(1000);
		readAccount.setSecondWaitTime(0);
		writeAccount.setAccountNumber(123);
		writeAccount.setAmount(456.77);
		writeAccount.setWaitTime(2000);
		latch = new CountDownLatch(1);
		defaultExecutor.submit(writeAccount);
		defaultExecutor.submit(readAccount);
		latch.await(3000, MILLISECONDS);
		assertEquals("the transaction B add an amount to the credit", 6012.639999999999, writeAccount.getResult(), 0.0);
		assertEquals("the amount in the transaction A is not changed because transaction B is not ended", 5555.87,
				readAccount.getFirstResult(), 0.0);
	}

	@Test
	public void testNonrepeatableRead() throws Exception {
		logger.info("start nonrepeatable read");
		readAccount.setAccountNumber(345);
		readAccount.setFirstWaitTime(0);
		readAccount.setSecondWaitTime(2000);
		writeAccount.setAccountNumber(345);
		writeAccount.setAmount(456.77);
		writeAccount.setWaitTime(1000);
		latch = new CountDownLatch(1);
		defaultExecutor.submit(writeAccount);
		defaultExecutor.submit(readAccount);
		latch.await(3000, MILLISECONDS);
		assertEquals("the transaction B add an amount to the credit", 3012.64, writeAccount.getResult(), 0.0);
		assertEquals("the first read of the transaction A before the transaction B ends", 2555.87,
				readAccount.getFirstResult(), 0.0);
		assertEquals("the second read of the transaction A after the transaction B ends", 2555.87,
				readAccount.getSecondResult(), 0.0);
	}

	@Test
	public void testPhantomRead() throws Exception {
		logger.info("start phantom read");
		queryReadAccount.setFirstWaitTime(0);
		queryReadAccount.setSecondWaitTime(2000);
		queryWriteAccount.setAccountNumber(953);
		queryWriteAccount.setAmount(456.77);
		queryWriteAccount.setWaitTime(1000);
		latch = new CountDownLatch(1);
		defaultExecutor.submit(queryWriteAccount);
		defaultExecutor.submit(queryReadAccount);
		latch.await(3000, MILLISECONDS);
		assertEquals("the transaction B add a new account", 456.77, queryWriteAccount.getResult(), 0.0);
		assertEquals("the first query in the transaction A before the transaction ends", 8,
				queryReadAccount.getFirstResult());
		assertEquals("the second query in the transaction A after the transaction ends", 9,
				queryReadAccount.getSecondResult());
	}
}
