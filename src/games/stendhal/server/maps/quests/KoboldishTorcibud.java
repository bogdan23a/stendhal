/***************************************************************************
 *                   (C) Copyright 2003-2010 - Stendhal                    *
 ***************************************************************************
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 ***************************************************************************/

package games.stendhal.server.maps.quests;
 
import games.stendhal.common.Grammar;
import games.stendhal.common.MathHelper;
import games.stendhal.common.Rand;
import games.stendhal.server.core.engine.SingletonRepository;
import games.stendhal.server.entity.item.StackableItem;
import games.stendhal.server.entity.npc.ChatAction;
import games.stendhal.server.entity.npc.ConversationPhrases;
import games.stendhal.server.entity.npc.ConversationStates;
import games.stendhal.server.entity.npc.EventRaiser;
import games.stendhal.server.entity.npc.SpeakerNPC;
import games.stendhal.server.entity.npc.action.CollectRequestedItemsAction;
import games.stendhal.server.entity.npc.action.DecreaseKarmaAction;
import games.stendhal.server.entity.npc.action.IncreaseKarmaAction;
import games.stendhal.server.entity.npc.action.IncreaseXPAction;
import games.stendhal.server.entity.npc.action.MultipleActions;
import games.stendhal.server.entity.npc.action.SayRequiredItemsFromCollectionAction;
import games.stendhal.server.entity.npc.action.SayTimeRemainingUntilTimeReachedAction;
import games.stendhal.server.entity.npc.action.SetQuestAction;
import games.stendhal.server.entity.npc.action.SetQuestToFutureRandomTimeStampAction;
import games.stendhal.server.entity.npc.condition.AndCondition;
import games.stendhal.server.entity.npc.condition.NotCondition;
import games.stendhal.server.entity.npc.condition.OrCondition;
import games.stendhal.server.entity.npc.condition.QuestActiveCondition;
import games.stendhal.server.entity.npc.condition.QuestCompletedCondition;
import games.stendhal.server.entity.npc.condition.QuestInStateCondition;
import games.stendhal.server.entity.npc.condition.QuestNotActiveCondition;
import games.stendhal.server.entity.npc.condition.QuestNotCompletedCondition;
import games.stendhal.server.entity.npc.condition.QuestNotInStateCondition;
import games.stendhal.server.entity.npc.condition.QuestNotStartedCondition;
import games.stendhal.server.entity.npc.condition.TimeReachedCondition;
import games.stendhal.server.entity.npc.parser.Sentence;
import games.stendhal.server.entity.player.Player;
import games.stendhal.server.util.ItemCollection;
import games.stendhal.server.util.TimeUtil;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import marauroa.common.game.IRPZone;

/**
 * QUEST: V.S.O.P. Koboldish Torcibud
 * <p>
 * PARTICIPANTS: <ul><li> Wrviliza, the Kobold Barmaid in Wo'fol bar </ul>
 * 
 * STEPS: <ul><li> Wrviliza will ask some items and ingredients 
 * to refurbish her stock of supplies for making her famous koboldish torcibud
 * <li> gather the items and ingredients and bring them to the bar
 * <li> some bottles of V.S.O.P. Koboldish Torcibud will be put on the bar counter
 * <li> drinking a bottle of V.S.O.P. Koboldish Torcibud will heal lost HP
 * <li> accepting the quest grants some Karma
 * <li> rejecting the quest wastes some Karma
 * </ul>
 *
 * REWARD: <ul><li>healing V.S.O.P. Koboldish Torcibud <li>500 XP</ul>
 *
 * REPETITIONS: <ul><li> unlimited
 * <li>once every 3 to 6 days (randomly determined at quest completion time</ul>
 *
 * @author omero
 */
public class KoboldishTorcibud extends AbstractQuest {
 
    public static final String QUEST_SLOT = "koboldish_torcibud";

    // the torcibud quest cannot be repeated before 3 to 6 days
    private static final int MIN_DELAY = 3 * MathHelper.MINUTES_IN_ONE_DAY;
    private static final int MAX_DELAY = 6 * MathHelper.MINUTES_IN_ONE_DAY;

    // how much XP is given as the reward
    private static final int XP_REWARD = 500;

    // a template of the items that wrviliza will ask for the quest,
    // it is only used to initialize the triggers in phase_2.
    private static final String REQUIRED_ITEMS_TEMPLATE =
        "eared bottle=0;" +
        "slim bottle=0;" +
        "mandragora=0;" +
        "artichoke=0;" +
        "arandula=0;" +
        "sclaria=0;" +
        "kekik=0;" +
        "fierywater=0";

    @Override
    public void addToWorld() {
        super.addToWorld();
        fillQuestInfo(
            "Koboldish Torcibud",
            "Wrviliza needs some stuff to prepare her famous Koboldish Torcibud.",
            true);
        phase_1();
        phase_2();
    }

    @Override
    public List<String> getHistory(final Player player) {

        final List<String> res = new ArrayList<String>();

        if (!player.hasQuest(QUEST_SLOT)) {
            return res;
        }

        res.add("I made acquaintance with Wrviliza, the kobold barmaid in Wo'fol bar.");

        final String questState = player.getQuest(QUEST_SLOT, 0);

        if ("rejected".equals(questState)) {
            res.add("She asked me to help her refurbish her stock of supplies for preparing her Koboldish Torcibud, "
                + " but I had more pressing matters to attend.");
        } else if ("done".equals(questState)) {
            long timestamp;
            try {
                timestamp = Long.parseLong(player.getQuest(QUEST_SLOT, 1));
            } catch (final NumberFormatException e) {
                timestamp = 0;
            }
            final long timeRemaining = (timestamp - System.currentTimeMillis());
            res.add("I helped her refurbish her stock of supplies for preparing her Koboldish Torcibud "
                + " and she will not need any more stuff before " 
                + TimeUtil.approxTimeUntil((int) (timeRemaining / 1000L)) + ".");
        } else {
            final ItemCollection missingItems = new ItemCollection();
            missingItems.addFromQuestStateString(player.getQuest(QUEST_SLOT));
            res.add("I'm helping her refurbish her stock of supplies for preparing her Koboldish Torcibud."
                + " I still have to bring her " + Grammar.enumerateCollection(missingItems.toStringListWithHash()));
        }

        return res;
    }
 
    @Override
    public String getSlotName() {
        return QUEST_SLOT;
    }
 
    @Override
        public String getName() {
        return "KoboldishTorcibud";
    }

    /**
     * Returns the items to collect and required quantity
     *
     * @param pLevel The level of the player undertaking the quest, affects the required quantity of some items.
     * @return A string composed of semi-colon separated key=value token pairs.
     */
    private String getRequiredItemsCollection(int pLevel) {

        int FACTOR_BOTTLE_SLIM = 60;
        int FACTOR_BOTTLE_EARED = 80;
		int FACTOR_MANDRAGORA = 100;

        int required_bottle_eared = Rand.roll1D6() + (pLevel / FACTOR_BOTTLE_EARED);
        int required_bottle_slim = Rand.roll1D6() + (pLevel / FACTOR_BOTTLE_SLIM);
		int required_mandragora = Rand.randUniform(1,3) + (pLevel / FACTOR_MANDRAGORA);

        return
            "eared bottle=" + required_bottle_eared + ";" +
            "slim bottle=" + required_bottle_slim + ";" +
            "mandragora=" + required_mandragora + ";" +
            "artichoke=" + Rand.roll1D6() + ";" +
            "arandula=" + Rand.roll1D6() + ";" +
            "sclaria=" + Rand.roll1D6() + ";" +
            "kekik=" + Rand.roll1D6() + ";" +
            "fierywater=" + Rand.roll1D6() + ";";
    }

    /**
     * The player meets the Kobold Barmaid Wrviliza and possibly gets a quest from her
     */
    public void phase_1() {
     
        final SpeakerNPC npc = npcs.get("Wrviliza");

        // player sends his greetings and never asked for a quest handled in NPC class

        // player sends his greetings to Wrviliza and has rejected the quest in the past
        npc.add(ConversationStates.IDLE,
            ConversationPhrases.GREETING_MESSAGES,
            new QuestInStateCondition(QUEST_SLOT, 0, "rejected"),
            ConversationStates.QUEST_OFFERED,
            "Wroff! Welcome back wanderer... Are you back to help me gather #stuff to make good #torcibud this time?",
            null);

        // player asks for a quest
        npc.add(ConversationStates.ATTENDING,
            ConversationPhrases.QUEST_MESSAGES,
            new QuestNotStartedCondition(QUEST_SLOT),
            ConversationStates.QUEST_OFFERED,
            "Wrof! My stock of supplies for preparing koboldish #torcibud is running thin. Would you help me getting some #stuff?",
            null
        );

        // player has done the quest already but not enough time has passed since completing it
        npc.add(ConversationStates.ATTENDING,
            ConversationPhrases.QUEST_MESSAGES,
            new AndCondition(
                new QuestCompletedCondition(QUEST_SLOT),
                new TimeReachedCondition(QUEST_SLOT, 1)),
            ConversationStates.QUEST_OFFERED,
            "Wroff! Indeed I'd need some #stuff for making some more koboldish #torcibud. Will you help?",
            null);

        // player has done the quest already but it's too early to get another
        npc.add(ConversationStates.ATTENDING,
            ConversationPhrases.QUEST_MESSAGES,
            new AndCondition(
                new QuestCompletedCondition(QUEST_SLOT),
                new NotCondition(new TimeReachedCondition(QUEST_SLOT, 1))),
            ConversationStates.ATTENDING, null,
            new SayTimeRemainingUntilTimeReachedAction(QUEST_SLOT, 1,
                "Wrof! Thank you but my stock of supplies for making good #torcibud will be fine for"));

        // player is curious about torcibud when offered the quest
        npc.add(ConversationStates.QUEST_OFFERED,
            "torcibud",
            new QuestNotStartedCondition(QUEST_SLOT),
            ConversationStates.QUEST_OFFERED,
            "Wruff. I will make more when I have enough #stuff! Are you going to help?",
            null);

        // player is curious about stuff, ingredients or supplies when offered the quest
        // note that the answer given by the NPC does not contain any trigger words on pourpose
        // as it should only hint what's kind of things are needed.
        // some details about the required items are given only once the quest has been accepted.
        npc.add(ConversationStates.QUEST_OFFERED,
            Arrays.asList("stuff","ingredients","supplies"),
            new OrCondition(
                new QuestNotStartedCondition(QUEST_SLOT),
                new QuestInStateCondition(QUEST_SLOT, 0, "rejected")),
            ConversationStates.QUEST_OFFERED,
            "Wrof! Some bottles, artichokes, a few herbs and fierywater... Things like that. So, will you help?",
            null);

        // player accepts the quest and gets to know what Wrviliza needs (switch to phase_2)
        npc.add(ConversationStates.QUEST_OFFERED,
            ConversationPhrases.YES_MESSAGES, null,
            ConversationStates.QUESTION_1, null,
            new MultipleActions(
            		new ChatAction() {
            			public void fire(final Player player, final Sentence sentence, final EventRaiser raiser) {
            				int pLevel = player.getLevel();
            				player.setQuest(QUEST_SLOT, 0, getRequiredItemsCollection(pLevel));
            			}
            		},
            		new IncreaseKarmaAction(20),
            		// here we have been careful to say the items from the collection only after the quest slot was set, 
            		// because in this quest, the amounts depend on level, above.
            		new SayRequiredItemsFromCollectionAction(QUEST_SLOT, "Wroff! Right now, I need [items]. Do you by chance have anything of that with you already?")));

        // player is not inclined to comply with the request and has not already rejected it once
        npc.add(ConversationStates.QUEST_OFFERED,
            ConversationPhrases.NO_MESSAGES,
            new AndCondition(
                new QuestNotActiveCondition(QUEST_SLOT),
                new QuestNotInStateCondition(QUEST_SLOT, 0, "rejected")),
            ConversationStates.ATTENDING,
            "Wruff... I guess I will have to ask to someone with a better attitude!",
            new MultipleActions(
            		new SetQuestAction(QUEST_SLOT, 0, "rejected"),
            		new DecreaseKarmaAction(20.0)));

        //player is not inclined to comply with the request and he has rejected it last time
        //If player wants to buy any beverage here, he should really take the quest now
        //Wrviliza holds a grudge by turning idle again.
        npc.add(ConversationStates.QUEST_OFFERED,
            ConversationPhrases.NO_MESSAGES,
            new QuestInStateCondition(QUEST_SLOT, 0, "rejected"),
            ConversationStates.IDLE,
            "Wruff... I guess you will wander around with a dry gulch then...",
            new MultipleActions(
            		new SetQuestAction(QUEST_SLOT, 0, "rejected"),
            		new DecreaseKarmaAction(20.0)));
    }

    /**
     * The player meets the Kobold Barmaid Wrviliza after he has started the quest
     */
    public void phase_2() {

        final SpeakerNPC npc = npcs.get("Wrviliza");

        // player says his greetings to Wrviliza and the quest is running
        npc.add(ConversationStates.IDLE,
            ConversationPhrases.GREETING_MESSAGES,
            new QuestActiveCondition(QUEST_SLOT),
            ConversationStates.QUESTION_1,
            "Wrof! Welcome back. Did you gather any #stuff for me?",
            null);

        // player is curious about artichokes
        npc.add(ConversationStates.ATTENDING,
            Arrays.asList("artichoke","artichokes"),
            new QuestActiveCondition(QUEST_SLOT),
            ConversationStates.ATTENDING,
            "Wrof! Not so common vegetables,"
                + " but I know that there are some being grown outside a farm"
                + " near Semos city.",
            null);

        // player is curious about fierywater
        npc.add(ConversationStates.ATTENDING,
            Arrays.asList("fierywater"),
            new QuestActiveCondition(QUEST_SLOT),
            ConversationStates.ATTENDING,
            "Wroof! Powerful fluid, that is."
                + " I buy mine in Ados market whenever I got the time to make a trip there.",
            null);

        // player is curious about herbs
        npc.add(ConversationStates.ATTENDING,
            Arrays.asList("arandula","sclaria","kekik"),
            new QuestActiveCondition(QUEST_SLOT),
            ConversationStates.ATTENDING,
            "Wrof! Common herb that grow outside in the plains or woods... Easy to spot!",
            null);

        // player is curious about mandragora
        npc.add(ConversationStates.ATTENDING,
            Arrays.asList("mandragora"),
            new QuestActiveCondition(QUEST_SLOT),
            ConversationStates.ATTENDING,
            "Wrof! All I know is that it is rare root that grows better outside in the woods,"
                + " expecially near places soaked with magic..."
                + " Not easy to spot either!",
            null);

        // player says stuff to be reminded of what is still missing
        npc.add(ConversationStates.QUESTION_1,
            "stuff", null,
            ConversationStates.QUESTION_1,
            null,
            new SayRequiredItemsFromCollectionAction(QUEST_SLOT, "Wrof! I still need [items]. Did you bring anything of that sort?"));

        // player answers yes when asked if he has brought any items
        npc.add(ConversationStates.QUESTION_1,
            ConversationPhrases.YES_MESSAGES, null,
            ConversationStates.QUESTION_1,
            "Fine, what did you bring?",
            null);
    
        // player answers no when asked if he has brought any items
        npc.add(ConversationStates.QUESTION_1,
            ConversationPhrases.NO_MESSAGES,
            new QuestNotCompletedCondition(QUEST_SLOT),
            ConversationStates.ATTENDING,
            "Wruf! Take your time... No hurry!",
            null);

        // create the ChatAction to reward the player
        ChatAction addRewardAction = new ChatAction() {
            public void fire(final Player player, final Sentence sentence, final EventRaiser npc) {
                final StackableItem
                    koboldish_torcibud_vsop = (StackableItem)
                        SingletonRepository
                            .getEntityManager().getItem("vsop koboldish torcibud");
                final int torcibud_bottles = 1 + Rand.roll1D6();
                koboldish_torcibud_vsop.setQuantity(torcibud_bottles);
                koboldish_torcibud_vsop.setBoundTo(player.getName());
                // vsop torcibud will heal up to 75% of the player's base HP he has when getting rewarded
                // all vsop koboldish torcibud is persistent (set in the xml for this item) so this value will last
                koboldish_torcibud_vsop.put("amount", player.getBaseHP()*75/100);

                //player.equipOrPutOnGround(koboldish_torcibud_vsop);
                //put the rewarded bottles on the counter
                final IRPZone zone = SingletonRepository.getRPWorld().getZone("int_wofol_bar");
                koboldish_torcibud_vsop.setPosition(3, 3);
                zone.add(koboldish_torcibud_vsop);

                npc.say(
                    "Wrof! Here take "
                    + Integer.toString(torcibud_bottles)
                    + " bottles of my V.S.O.P. Koboldish Torcibud with my best wishes for you!");
            }
        };

        // player collected all the items. grant the XP before handing out the torcibud
        ChatAction completeAction = new MultipleActions(
            new SetQuestAction(QUEST_SLOT, 0, "done"),
            new SetQuestToFutureRandomTimeStampAction(QUEST_SLOT, 1, MIN_DELAY, MAX_DELAY),
            new IncreaseXPAction(XP_REWARD),
            addRewardAction);

        // create the ChatAction used for item triggers
        final ChatAction itemsChatAction = new CollectRequestedItemsAction(
            QUEST_SLOT,
            "Wroff! do you have anything else?",
            "Wruff! You have already brought that to me!",
            completeAction,
            ConversationStates.ATTENDING);
        
        // add triggers for the item names
        final ItemCollection items = new ItemCollection();
        items.addFromQuestStateString(REQUIRED_ITEMS_TEMPLATE);
        for (final Map.Entry<String, Integer> item : items.entrySet()) {
            npc.add(ConversationStates.QUESTION_1,
                item.getKey(), null,
                ConversationStates.QUESTION_1, null,
                itemsChatAction);
        }
    }
}
