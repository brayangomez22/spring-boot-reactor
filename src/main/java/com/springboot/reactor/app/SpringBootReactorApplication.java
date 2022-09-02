package com.springboot.reactor.app;

import com.springboot.reactor.app.model.Comments;
import com.springboot.reactor.app.model.User;
import com.springboot.reactor.app.model.UserWithComments;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;

@SpringBootApplication
public class SpringBootReactorApplication implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(SpringBootReactorApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(SpringBootReactorApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		backpressureWithOperatorExample();
	}

	public void backpressureWithOperatorExample() {
		Flux.range(1,10)
				.log()
				.limitRate(5)
				.subscribe();
	}

	public void backpressureExample() {
		Flux.range(1,10)
				.log()
				.subscribe(new Subscriber<Integer>() {
					private Subscription subscription;
					private Integer limit = 2;
					private Integer consumed = 0;

					@Override
					public void onSubscribe(Subscription subscription) {
						this.subscription = subscription;
						subscription.request(limit);
					}

					@Override
					public void onNext(Integer integer) {
						log.info(integer.toString());
						consumed++;
						if(consumed.equals(limit)) {
							consumed = 0;
							subscription.request(limit);
						}
					}

					@Override
					public void onError(Throwable throwable) {

					}

					@Override
					public void onComplete() {

					}
				});
	}

	public void intervalFromCreateExample() throws InterruptedException {
		Flux.create(emitter -> {
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				private Integer counter = 0;

				@Override
				public void run() {
					emitter.next(++counter);
					if(counter == 10) {
						timer.cancel();
						emitter.complete();
					}
				}
			}, 1000, 1000);
		})
		.subscribe(next -> log.info(next.toString()),
			error -> log.error(error.getMessage()),
			() -> log.info("We're done"));
	}

	public void infiniteIntervalExample() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);

		Flux.interval(Duration.ofSeconds(1))
				.doOnTerminate(latch::countDown)
				.flatMap(i -> {
					if(i >= 5) return Flux.error(new InterruptedException("only up to 5"));
					return Flux.just(i);
				})
				.map(i -> "Hello "+i)
				.retry(2)
				.subscribe(log::info, e -> log.error(e.getMessage()));

		latch.await();
	}

	public void delayExample() throws InterruptedException {
		Flux<Integer> range = Flux.range(1,12)
				.delayElements(Duration.ofSeconds(1))
				.doOnNext(integer -> log.info(integer.toString()));

		range.subscribe();
		Thread.sleep(13000);
	}

	public void intervalExample() throws Exception {
		Flux<Integer> range = Flux.range(1,12);
		Flux<Long> interval = Flux.interval(Duration.ofSeconds(1));

		range.zipWith(interval, (ra, in) -> ra)
				.doOnNext(i -> log.info(i.toString()))
				.blockLast();
	}

	public void zipWithRangeExample() throws Exception {
		Flux<Integer> ranges = Flux.range(0,4);

		Flux.just(1, 2, 3, 4)
				.map(n -> (n*2))
				.zipWith(ranges, (one, two) -> String.format("First flux %d, Second flux %d", one, two))
				.subscribe(log::info);
	}

	public User createUser() {
		return new User("John", "Doe");
	}

	public Comments createComments() {
		Comments comments = new Comments();
		comments.addComment("Hello Pepe!!!");
		comments.addComment("How are you?");
		comments.addComment("Bye!");

		return comments;
	}

	public void userWithCommentsZipWithExampleTwo() throws Exception {
		Mono<User> userMono = Mono.fromCallable(this::createUser);
		Mono<Comments> commentsMono = Mono.fromCallable(this::createComments);

		Mono<UserWithComments> userWithComments = userMono
				.zipWith(commentsMono)
				.map(tuple -> {
					User u = tuple.getT1();
					Comments c = tuple.getT2();
					return new UserWithComments(u,c);
				});
		userWithComments.subscribe(user -> log.info(user.toString()));
	}

	public void userWithCommentsZipWithExample() throws Exception {
		Mono<User> userMono = Mono.fromCallable(this::createUser);
		Mono<Comments> commentsMono = Mono.fromCallable(this::createComments);

		Mono<UserWithComments> userWithComments = userMono.zipWith(commentsMono, UserWithComments::new);
		userWithComments.subscribe(user -> log.info(user.toString()));
	}

	public void userWithCommentsFlatMapExample() throws Exception {
		Mono<User> userMono = Mono.fromCallable(this::createUser);
		Mono<Comments> commentsMono = Mono.fromCallable(this::createComments);

		userMono.flatMap(u -> commentsMono.map(c -> new UserWithComments(u,c)))
				.subscribe(user -> log.info(user.toString()));
	}

	public void collectListExample() throws Exception {
		List<User> usersList = new ArrayList<>();
		usersList.add(new User("Brayan", "Guzman"));
		usersList.add(new User("David", "Gonzales"));
		usersList.add(new User("Ximena", "Gonzales"));
		usersList.add(new User("Pepe", "Martinez"));
		usersList.add(new User("Bruce", "Lee"));
		usersList.add(new User("Bruce", "Willis"));

		Flux.fromIterable(usersList)
				.collectList()
				.subscribe(list -> log.info(list.toString()));
	}

	public void toStringExample() throws Exception {
		List<User> usersList = new ArrayList<>();
		usersList.add(new User("Brayan", "Guzman"));
		usersList.add(new User("David", "Gonzales"));
		usersList.add(new User("Ximena", "Gonzales"));
		usersList.add(new User("Pepe", "Martinez"));
		usersList.add(new User("Bruce", "Lee"));
		usersList.add(new User("Bruce", "Willis"));

		Flux.fromIterable(usersList)
				.map(user -> user.getName().toUpperCase().concat(" ").concat(user.getSurname().toUpperCase()))
				.flatMap(name -> {
					if(name.contains("bruce".toUpperCase())) {
						return Mono.just(name);
					} else {
						return Mono.empty();
					}
				})
				.map(String::toLowerCase)
				.subscribe(user -> log.info(user.toString()));
	}

	public void flatMapExample() throws Exception {
		List<String> usersList = new ArrayList<>();
		usersList.add("Brayan Guzman");
		usersList.add("David Gonzales");
		usersList.add("Ximena Gonzales");
		usersList.add("Pepe Martinez");
		usersList.add("Bruce Lee");
		usersList.add("Bruce Willis");

		Flux.fromIterable(usersList)
				.map(name -> new User(name.split(" ")[0].toUpperCase(), name.split(" ")[1].toUpperCase()))
				.flatMap(user -> {
					if(user.getName().equalsIgnoreCase("bruce")) {
						return Mono.just(user);
					} else {
						return Mono.empty();
					}
				})
				.map(user -> {
					String name = user.getName().toLowerCase();
					String surname = user.getSurname().toLowerCase();
					user.setName(name);
					user.setSurname(surname);
					return user;
				})
				.subscribe(user -> log.info(user.toString()));
	}

	public void iterableExample() throws Exception {
		List<String> usersList = new ArrayList<>();
		usersList.add("Brayan Guzman");
		usersList.add("David Gonzales");
		usersList.add("Ximena Gonzales");
		usersList.add("Pepe Martinez");
		usersList.add("Bruce Lee");
		usersList.add("Bruce Willis");

		//Flux<String> names = Flux.just("Brayan Guzman", "David Gonzales", "Ximena Gonzales", "Pepe Martinez", "Bruce Lee", "Bruce Willis");
		Flux<String> names = Flux.fromIterable(usersList);

		Flux<User> users = names
				.map(name -> new User(name.split(" ")[0].toUpperCase(), name.split(" ")[1].toUpperCase()))
				.filter(user -> user.getName().toLowerCase().equals("bruce"))
				.doOnNext(user -> {
					if(user == null) throw new RuntimeException("Names cannot be empty");
					System.out.println(user.getName().concat(" ").concat(user.getSurname()));
				})
				.map(user -> {
					String name = user.getName().toLowerCase();
					String surname = user.getSurname().toLowerCase();
					user.setName(name);
					user.setSurname(surname);
					return user;
				});

		users.subscribe(user -> log.info(user.toString()),
				error -> log.error(error.getMessage()),
				new Runnable() {
					@Override
					public void run() {
						log.info("The execution of the observable has finished successfully");
					}
				}
		);
	}
}
