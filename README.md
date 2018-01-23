# OPML Alerts

[![Build Status](https://travis-ci.org/andrejtokarcik/akka-typed-opmlalerts.svg?branch=master)](https://travis-ci.org/andrejtokarcik/akka-typed-opmlalerts)

OPML Alerts is an RSS/Atom-based change detection and notification service, a simplified analogue of its namesake Google Alerts.

The application takes an OPML file containing a list of web feeds, annotated with patterns in form of regular expressions. The feeds get polled in regular user-configurable intervals for new entries, which are subsequently filtered and reported in accordance with the criteria imposed by the patterns.

OPML Alerts is written in Scala and its primary purpose is to provide a demo of [Akka Typed](https://doc.akka.io/docs/akka/2.5.9/actors-typed.html), which is an actively developed branch intended to overcome the design flaws of the currently standard, untyped Akka. Since the new API has not yet been officially released, this project is meant more as a playground for the new technology rather than as a stable product.

Parsing of both the OPML files and the RSS/Atom feeds is delegated to the [ROME framework](https://rometools.github.io/rome/).

## An annotated OPML file

```
<opml version="1.0">
 <body>
  <outline type="link" text="GitHub Timeline" url="https://github.com/timeline"
    interval="20" pattern="(for|while|if)\s*\(" />
  <outline url="https://twitrss.me/twitter_search_to_rss/?term=scala" pattern="(?i)haskell|java" />
  <outline url="https://twitrss.me/twitter_search_to_rss/?term=haskell" pattern="(?i)monad|functor" />
 </body>
</opml>
```

OPML Alerts is guided by the following special attributes of the `<outline />` tag:

* `pattern`: A mandatory attribute for the feed to be considered by OPML Alerts. Defines the regular expression to be matched against the text content of feed entries.  See the documentation for [`java.util.regex.Pattern`](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html) for details about the regular expression syntax.
* `interval`: An optional attribute. Determines the length of interval in seconds between two consecutive pollings of the feed. Defaults to 60 seconds.

Regarding the common attributes of `<outline />`, only a `url`/`xmlUrl`/`htmlUrl` is strictly necessary. Nonetheless, presence of other attributes (such as `title` or `text`) may lead to more informative result reports.


## Actor structure

1. When started, OPML Alerts spawns a [`Manager`](src/main/scala/opmlalerts/Manager.scala) actor that oversees all the stages and serves as a common communication point for all the other actors.
2. The ROME-based [`Parser`](src/main/scala/opmlalerts/Parser.scala) class is called to parse the input OPML file.
3. `Manager` dedicates a separate [`FeedHandler`](src/main/scala/opmlalerts/FeedHandler.scala) actors to each feed.
4. A pool of [`EntryHandler`s](src/main/scala/opmlalerts/EntryHandler.scala) is also spawned; these are fungible and not tied to particular feeds.
5. A collection of internal timers is set up to trigger feed re-fetching whereupon the associated `FeedHandler` GETs the feed URL and sends the yet-unseen entries back to the `Manager`.
6. The entries are distributed among the `EntryHandler`s by a [round-robin scheduler](src/main/scala/opmlalerts/ImmutableRoundRobin.scala).
7. An `EntryHandler` scrapes the web page pointed to by the given feed entry and matches it against the pattern.
8. At last, a summary of the match results is displayed by all available [`Printer`](src/main/scala/opmlalerts/Printer.scala) instances.

## Used features of Akka Typed

The following concepts and features of Akka Typed can be seen in action with OPML Alerts:

* Extensive employment of (memoised) [adapter actors](https://doc.akka.io/api/akka/current/akka/actor/typed/scaladsl/ActorContext.html#spawnAdapter[U](f:U=%3ET):akka.actor.typed.ActorRef[U]) together with private message classes to guarantee protocol safety, viz. to avoid identifying information as components of response messages.
* [`Actor.withTimers`](https://akka.io/blog/2017/05/26/timers) factory to configure a periodic timer for each feed interval.
* [Actor discovery](https://doc.akka.io/docs/akka/2.5.9/actor-discovery-typed.html). Instances of `Printer` are discovered and registered automatically through registration & subscription with the `Receptionist` actor.
* [Coexistence](https://doc.akka.io/docs/akka/2.5.9/coexisting.html) of typed and untyped actors within a single `ActorSystem`. OPML Alerts uses the [schwatcher](https://github.com/lloydmeta/schwatcher) file-watching library to detect and react to any chadges made to the OPML file. However, the library is written using untyped actors; the [root behavior](src/main/scala/opmlalerts/Main.scala) therefore guards an `ActorSystem` which, even though typed in itself, accomodates untyped elements through adapters.
* [Synchronous testing](https://doc.akka.io/docs/akka/2.5.9/testing-typed.html) of behaviors as such, instead of as instantiated within actors.
