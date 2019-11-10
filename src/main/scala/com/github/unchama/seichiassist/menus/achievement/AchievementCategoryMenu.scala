package com.github.unchama.seichiassist.menus.achievement

import cats.effect.IO
import com.github.unchama.itemstackbuilder.{IconItemStackBuilder, SkullItemStackBuilder}
import com.github.unchama.menuinventory.slot.button.Button
import com.github.unchama.menuinventory.slot.button.action.LeftClickButtonEffect
import com.github.unchama.menuinventory.{Menu, MenuFrame, MenuSlotLayout}
import com.github.unchama.seichiassist.achievement.hierarchy.AchievementCategory._
import com.github.unchama.seichiassist.achievement.hierarchy.AchievementGroup._
import com.github.unchama.seichiassist.achievement.hierarchy.{AchievementCategory, AchievementGroup}
import com.github.unchama.seichiassist.menus.{ColorScheme, CommonButtons}
import com.github.unchama.seichiassist.{CommonSoundEffects, SkullOwners}
import org.bukkit.ChatColor._
import org.bukkit.Material
import org.bukkit.entity.Player

object AchievementCategoryMenu {
  import com.github.unchama.seichiassist.concurrent.PluginExecutionContexts.layoutPreparationContext

  type AchievementGroupRepr = (AchievementGroup, Material)

  def groupsLayoutFor(achievementCategory: AchievementCategory): Map[Int, AchievementGroupRepr] =
    achievementCategory match {
      case BrokenBlock =>
        Map(
          9 * 1 + 3 -> (BrokenBlockAmount, Material.IRON_PICKAXE),
          9 * 1 + 5 -> (BrokenBlockRanking, Material.DIAMOND_PICKAXE)
        )
      case Building =>
        Map()
      case Login =>
        Map(
          9 * 1 + 1 -> (PlayTime, Material.COMPASS),
          9 * 1 + 3 -> (TotalLogins, Material.BOOK),
          9 * 1 + 5 -> (ConsecutiveLogins, Material.BOOK_AND_QUILL),
          9 * 1 + 7 -> (Anniversaries, Material.NETHER_STAR)
        )
      case Challenges =>
        Map(
          9 * 1 + 3 -> (MebiusBreeder, Material.DIAMOND_HELMET),
          9 * 1 + 5 -> (StarLevel, Material.GOLD_INGOT)
        )
      case Specials =>
        Map(
          9 * 1 + 2 -> (OfficialEvent, Material.BLAZE_POWDER),
          9 * 1 + 4 -> (VoteCounts, Material.YELLOW_FLOWER),
          9 * 1 + 6 -> (Secrets, Material.DIAMOND_BARDING),
        )
    }

  def buttonFor(achievementGroupRepr: AchievementGroupRepr): Button =
    achievementGroupRepr match {
      case (group, material) =>
        val partialBuilder =
          new IconItemStackBuilder(material)
            .title(ColorScheme.navigation(s"実績「${group.name}」"))

        import com.github.unchama.targetedeffect._

        if (group.achievements.nonEmpty) {
          Button(
            partialBuilder
              .lore(s"${RED}獲得状況を表示します。")
              .build(),
            LeftClickButtonEffect(
              CommonSoundEffects.menuTransitionFenceSound,
              AchievementGroupMenu(group).open
            )
          )
        } else {
          Button(
            partialBuilder
              .lore(s"${RED}獲得状況を表示します。※未実装")
              .build(),
            LeftClickButtonEffect(emptyEffect)
          )
        }
    }

  def apply(category: AchievementCategory): Menu = {
    val groupButtons =
      groupsLayoutFor(category).view.mapValues(buttonFor).toMap

    val toMainMenuButton =
      CommonButtons.transferButton(
        new SkullItemStackBuilder(SkullOwners.MHF_ArrowLeft),
        "実績・二つ名メニューへ",
        AchievementMenu,
      )

    new Menu {
      import com.github.unchama.menuinventory.InventoryRowSize._

      override val frame: MenuFrame = MenuFrame(4.rows(), s"$DARK_PURPLE${BOLD}カテゴリ「${category.name}」")

      override def computeMenuLayout(player: Player): IO[MenuSlotLayout] =
        IO.pure(MenuSlotLayout(groupButtons ++ Map(9 * 3 -> toMainMenuButton)))
    }
  }
}