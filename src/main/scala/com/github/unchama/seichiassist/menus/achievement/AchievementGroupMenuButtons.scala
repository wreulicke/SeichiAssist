package com.github.unchama.seichiassist.menus.achievement

import cats.data.Kleisli
import cats.effect.IO
import com.github.unchama.itemstackbuilder.IconItemStackBuilder
import com.github.unchama.menuinventory.slot.button.action.LeftClickButtonEffect
import com.github.unchama.menuinventory.slot.button.{Button, RecomputedButton}
import com.github.unchama.seichiassist.SeichiAssist
import com.github.unchama.seichiassist.achievement.SeichiAchievement.{AutoUnlocked, Hidden, ManuallyUnlocked, Normal}
import com.github.unchama.seichiassist.achievement.TitleMapping.TitleCombination
import com.github.unchama.seichiassist.achievement.{SeichiAchievement, TitleMapping}
import com.github.unchama.targetedeffect.player.FocusedSoundEffect
import org.bukkit.ChatColor._
import org.bukkit.entity.Player
import org.bukkit.{Material, Sound}

case class AchievementGroupMenuButtons(viewer: Player) {
  def buttonFor(achievement: SeichiAchievement, hasUnlocked: Boolean): Button = {
    val itemStack = {
      val material = if (hasUnlocked) Material.DIAMOND_BLOCK else Material.BEDROCK
      val title = {
        val displayTitleName =
          TitleMapping.getTitleFor(achievement.id)
            .filter(_ => hasUnlocked)
            .getOrElse("???")

        s"$YELLOW$UNDERLINE${BOLD}No${achievement.id}「$displayTitleName」"
      }

      val lore = {
        val conditionDescriptionBlock =
          {
            achievement match {
              case normal: SeichiAchievement.Normal[_] =>
                List(normal.condition.condition)
              case hidden: SeichiAchievement.Hidden[_] =>
                val description =
                  if (hasUnlocked)
                    hidden.condition.condition.condition
                  else
                    hidden.condition.hiddenCondition
                List(description)
              case SeichiAchievement.GrantedByConsole(_, condition, explanation) =>
                List(condition) ++ explanation.getOrElse(Nil)
            }
          }.map(s"$RESET$RED" + _)

        val unlockSchemeDescription =
          achievement match {
            case _: AutoUnlocked =>
              List(s"$RESET$RED※この実績は自動解禁式です。")
            case m: ManuallyUnlocked =>
              m match {
                case _: Hidden[_] =>
                  List(s"$RESET$RED※この実績は手動解禁式です。")
                case _ =>
                  if (hasUnlocked)
                    List()
                  else
                    List(s"$RESET$GREEN※クリックで実績に挑戦できます")
              }
            case _ =>
              List(s"$RESET$RED※この実績は配布解禁式です。")
          }

        val hiddenDescription =
          achievement match {
            case _: Hidden[_] => List(s"$RESET${AQUA}こちらは【隠し実績】となります")
            case _ => Nil
          }

        conditionDescriptionBlock ++ unlockSchemeDescription ++ hiddenDescription
      }

      new IconItemStackBuilder(material)
        .title(title)
        .lore(lore)
        .build()
    }

    val clickEffect = {
      import com.github.unchama.targetedeffect.MessageEffects._
      import com.github.unchama.targetedeffect._

      val clickSound = FocusedSoundEffect(Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1f, 1f)

      val effect =
        if (hasUnlocked) {
          def setNickname(player: Player): Unit = {
            val TitleCombination(firstId, secondId, thirdId) =
              TitleMapping.mapping.get(achievement.id) match {
                case Some(value) => value
                case None =>
                  player.sendMessage(s"${RED}二つ名の設定に失敗しました。")
                  return
              }

            SeichiAssist
              .playermap(player.getUniqueId)
              .updateNickname(firstId.getOrElse(0), secondId.getOrElse(0), thirdId.getOrElse(0))
            player.sendMessage(s"二つ名「${TitleMapping.getTitleFor(achievement.id).get}」が設定されました。")
          }

          delay(setNickname)
        } else {
          achievement match {
            case _: AutoUnlocked =>
              s"${RED}この実績は自動解禁式です。毎分の処理をお待ちください。".asMessageEffect()
            case achievement: ManuallyUnlocked =>
              achievement match {
                case achievement: Normal[_] =>
                  Kleisli { player: Player =>
                    for {
                      shouldUnlock <- achievement.condition.shouldUnlock(player)
                      _ <- if (shouldUnlock) IO {
                        SeichiAssist.playermap(player.getUniqueId).TitleFlags.addOne(achievement.id)
                        player.sendMessage(s"実績No${achievement.id}を解除しました！おめでとうございます！")
                      } else {
                        s"${RED}実績No${achievement.id}は条件を満たしていません。".asMessageEffect()(player)
                      }
                    } yield ()
                  }
                case _ =>
                  s"$RESET$RED※この実績は手動解禁式です。".asMessageEffect()
              }
            case _ =>
              s"$RED※この実績は配布解禁式です。運営チームからの配布タイミングを逃さないようご注意ください。".asMessageEffect()
          }
        }

      sequentialEffect(clickSound, effect)
    }

    Button(itemStack, LeftClickButtonEffect(clickEffect))
  }

  import com.github.unchama.seichiassist.concurrent.PluginExecutionContexts.layoutPreparationContext

  def computeButtonFor(achievement: SeichiAchievement): IO[Button] = RecomputedButton {
    for {
      hasObtainedTheAchievement <- IO { SeichiAssist.playermap(viewer.getUniqueId).TitleFlags.contains(achievement.id) }
    } yield buttonFor(achievement, hasObtainedTheAchievement)
  }
}