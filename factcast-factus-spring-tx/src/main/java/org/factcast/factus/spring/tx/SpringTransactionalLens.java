package org.factcast.factus.spring.tx;

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.projector.AbstractTransactionalLens;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Function;

@Slf4j
public class SpringTransactionalLens extends AbstractTransactionalLens {
  private final PlatformTransactionManager transactionManager;
  private final SpringTxManager txManager;

  @VisibleForTesting
  SpringTransactionalLens(
      @NonNull SpringTxProjection springTxProjection,
      @NonNull SpringTxManager txManager,
      @NonNull TransactionDefinition definition) {
    super(springTxProjection);

    this.txManager = txManager;
    transactionManager = springTxProjection.platformTransactionManager();

    flushTimeout = calculateFlushTimeout(definition.getTimeout() * 1000L);
    bulkSize = Math.max(1, getSize(springTxProjection));
    log.trace(
        "Created {} instance for {} with batchsize={},timeout={}",
        getClass().getSimpleName(),
        springTxProjection,
        bulkSize,
        flushTimeout);
  }

  public SpringTransactionalLens(@NonNull SpringTxProjection springTxProjection) {
    this(
        springTxProjection,
        new SpringTxManager(
            springTxProjection.platformTransactionManager(), creatDefinition(springTxProjection)),
        creatDefinition(springTxProjection));
  }

  @Override
  public Function<Fact, ?> parameterTransformerFor(@NonNull Class<?> type) {
    if (TransactionTemplate.class.equals(type)) {
      return f -> new TransactionTemplate(transactionManager);
    }
    return null;
  }

  @Override
  public void beforeFactProcessing(@NonNull Fact f) {
    super.beforeFactProcessing(f);

    // start a new tx or join a current one
    txManager.startOrJoin();
  }

  @Override
  protected void doClear() {
    txManager.rollback();
  }

  @Override
  protected void doFlush() {
    txManager.commit();
  }

  @VisibleForTesting
  static int getSize(@NonNull SpringTxProjection p) {
    SpringTransactional transactional = p.getClass().getAnnotation(SpringTransactional.class);
    if (transactional == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + SpringTransactional.class.getSimpleName());
    }
    return transactional.bulkSize();
  }

  @VisibleForTesting
  static TransactionDefinition creatDefinition(@NonNull SpringTxProjection p) {
    SpringTransactional transactional = p.getClass().getAnnotation(SpringTransactional.class);
    if (transactional == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + SpringTransactional.class.getSimpleName());
    }
    return SpringTransactional.Defaults.with(transactional);
  }

  @VisibleForTesting
  static long calculateFlushTimeout(long timeoutInMs) {
    // "best" guess
    long flush = timeoutInMs / 10 * 8;
    if (flush < 80) {
      // disable batching altogether as it is too risky
      flush = 0;
    }
    return flush;
  }
}
