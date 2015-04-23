import java.nio.channels.CompletionHandler;

import rx.Observable;
import rx.functions.Action2;
import rx.functions.Action3;
import rx.functions.Action5;
import rx.functions.Action7;

public class NioRx {

    /**
     * Wraps an NIO asynchronous action in an Observable.
     * 
     * @param nioAction NIO action
     * @param param First action parameter
     * @return Observable that calls the wrapped action on subscription
     */
    public static <T1, R> Observable<R> wrap(Action3<T1, Void, CompletionHandler<R, Void>> nioAction, T1 param) {
        return Observable.create(subscription -> {
            CompletionHandler<R, Void> onCompleted = new CompletionHandler<R, Void>() {

                @Override
                public void failed(Throwable exc, Void attachment) {
                    if (!subscription.isUnsubscribed()) {
                        subscription.onError(exc);
                    }
                }

                @Override
                public void completed(R result, Void attachment) {
                    if (!subscription.isUnsubscribed()) {
                        subscription.onNext(result);
                        subscription.onCompleted();
                    }
                }
            };

            nioAction.call(param, null, onCompleted);
        });
    }

    /**
     * Wraps an NIO asynchronous action in an Observable.
     * 
     * @param nioAction NIO action
     * @return Observable that calls the wrapped action on subscription
     */
    public static <R> Observable<R> wrap(Action2<Void, CompletionHandler<R, Void>> nioAction) {
        return Observable.create(subscription -> {
            CompletionHandler<R, Void> onCompleted = new CompletionHandler<R, Void>() {

                @Override
                public void failed(Throwable exc, Void attachment) {
                    if (!subscription.isUnsubscribed()) {
                        subscription.onError(exc);
                    }
                }

                @Override
                public void completed(R result, Void attachment) {
                    if (!subscription.isUnsubscribed()) {
                        subscription.onNext(result);
                        subscription.onCompleted();
                    }
                }
            };

            nioAction.call(null, onCompleted);
        });
    }

    /**
     * Wraps an NIO asynchronous action in an Observable.
     * 
     * @param nioAction NIO action
     * @param param1 First action parameter
     * @param param2 Second action parameter
     * @param param3 Third action parameter
     * @return Observable that calls the wrapped action on subscription
     */
    public static <T1, T2, T3, R> Observable<R> wrap(
            Action5<T1, T2, T3, Void, CompletionHandler<R, Void>> nioAction,
            T1 param1,
            T2 param2,
            T3 param3) {
        return Observable.create(subscription -> {
            CompletionHandler<R, Void> onCompleted = new CompletionHandler<R, Void>() {

                @Override
                public void failed(Throwable exc, Void attachment) {
                    if (!subscription.isUnsubscribed()) {
                        subscription.onError(exc);
                    }
                }

                @Override
                public void completed(R result, Void attachment) {
                    if (!subscription.isUnsubscribed()) {
                        subscription.onNext(result);
                        subscription.onCompleted();
                    }
                }
            };

            nioAction.call(param1, param2, param3, null, onCompleted);
        });
    }

    /**
     * Wraps an NIO asynchronous action in an Observable.
     * 
     * @param nioAction NIO action
     * @param param1 First action parameter
     * @param param2 Second action parameter
     * @param param3 Third action parameter
     * @param param4 Fourth action parameter
     * @param param5 Fifth action parameter
     * @return Observable that calls the wrapped action on subscription
     */
    public static <T1, T2, T3, T4, T5, R> Observable<? super R> wrap(
            Action7<T1, T2, T3, T4, T5, Void, CompletionHandler<? super R, Void>> nioAction,
            T1 param1,
            T2 param2,
            T3 param3,
            T4 param4,
            T5 param5) {
        return Observable.create(subscription -> {
            CompletionHandler<R, Void> onCompleted = new CompletionHandler<R, Void>() {

                @Override
                public void failed(Throwable exc, Void attachment) {
                    if (!subscription.isUnsubscribed()) {
                        subscription.onError(exc);
                    }
                }

                @Override
                public void completed(R result, Void attachment) {
                    if (!subscription.isUnsubscribed()) {
                        subscription.onNext(result);
                        subscription.onCompleted();
                    }
                }
            };

            nioAction.call(param1, param2, param3, param4, param5, null, onCompleted);
        });
    }
}
