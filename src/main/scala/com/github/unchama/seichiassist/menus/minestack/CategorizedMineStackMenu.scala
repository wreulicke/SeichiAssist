package com.github.unchama.seichiassist.menus.minestack

import cats.effect.IO
import com.github.unchama.itemstackbuilder.{SkullItemStackBuilder, SkullOwnerReference}
import com.github.unchama.menuinventory.slot.button.action.ClickEventFilter
import com.github.unchama.menuinventory.slot.button.{Button, action}
import com.github.unchama.menuinventory.{InventoryRowSize, Menu, MenuFrame, MenuSlotLayout}
import com.github.unchama.seichiassist.minestack.MineStackObjectCategory
import com.github.unchama.seichiassist.{CommonSoundEffects, MineStackObjectList, SkullOwners}
import com.github.unchama.targetedeffect._
import org.bukkit.ChatColor._
import org.bukkit.entity.Player

object CategorizedMineStackMenu {

  import com.github.unchama.seichiassist.concurrent.PluginExecutionContexts.layoutPreparationContext

  private val mineStackObjectPerPage = 9 * 5

  /**
   * カテゴリ別マインスタックメニューで [pageIndex] + 1 ページ目の[Menu]
   */
  def forCategory(category: MineStackObjectCategory, pageIndex: Int = 0): Menu = new Menu {
    override val frame: MenuFrame =
      MenuFrame(Left(InventoryRowSize(6)), s"$DARK_BLUE${BOLD}MineStack(${category.uiLabel})")

    override def computeMenuLayout(player: Player): IO[MenuSlotLayout] =
      computeMenuLayoutOn(category, pageIndex)(player)
  }

  private def computeMenuLayoutOn(category: MineStackObjectCategory, page: Int)(player: Player): IO[MenuSlotLayout] = {
    import MineStackObjectCategory._
    import cats.implicits._

    val categoryItemList = MineStackObjectList.minestacklist.filter(_.category() == category)
    val totalNumberOfPages = Math.ceil(categoryItemList.size / 45.0).toInt

    // オブジェクトリストが更新されるなどの理由でpageが最大値を超えてしまった場合、最後のページを計算する
    if (page >= totalNumberOfPages) return computeMenuLayoutOn(category, totalNumberOfPages - 1)(player)

    val playerMineStackButtons = MineStackButtons(player)
    import playerMineStackButtons._

    val uiOperationSection = Sections.uiOperationSection(totalNumberOfPages)(category, page)

    // カテゴリ内のMineStackアイテム取り出しボタンを含むセクションの計算
    val categorizedItemSectionComputation =
      categoryItemList.slice(mineStackObjectPerPage * page, mineStackObjectPerPage * page + mineStackObjectPerPage)
        .map(getMineStackItemButtonOf)
        .zipWithIndex
        .map(_.swap)
        .toList
        .map(_.sequence)
        .sequence

    // 自動スタック機能トグルボタンを含むセクションの計算
    val autoMineStackToggleButtonSectionComputation =
      List((9 * 5 + 4) -> computeAutoMineStackToggleButton())
        .map(_.sequence)
        .sequence

    for {
      categorizedItemSection <- categorizedItemSectionComputation
      autoMineStackToggleButtonSection <- autoMineStackToggleButtonSectionComputation
      combinedLayout = uiOperationSection.++(categorizedItemSection).++(autoMineStackToggleButtonSection)
    } yield MenuSlotLayout(combinedLayout: _*)
  }

  object Sections {
    val mineStackMainMenuButtonSection: Seq[(Int, Button)] = {
      val mineStackMainMenuButton = Button(
        new SkullItemStackBuilder(SkullOwners.MHF_ArrowLeft)
          .title(s"$YELLOW$UNDERLINE${BOLD}MineStackメインメニューへ")
          .lore(List(s"$RESET$DARK_RED${UNDERLINE}クリックで移動"))
          .build(),
        action.FilteredButtonEffect(ClickEventFilter.ALWAYS_INVOKE) { _ =>
          sequentialEffect(
            CommonSoundEffects.menuTransitionFenceSound,
            MineStackMainMenu.open
          )
        }
      )

      Seq {
        9 * 5 -> mineStackMainMenuButton
      }
    }

    // ページ操作等のボタンを含むレイアウトセクション
    def uiOperationSection(totalNumberOfPages: Int)(category: MineStackObjectCategory, page: Int): Seq[(Int, Button)] = {
      def buttonToTransferTo(pageIndex: Int, skullOwnerReference: SkullOwnerReference) = Button(
        new SkullItemStackBuilder(skullOwnerReference)
          .title(s"$YELLOW$UNDERLINE${BOLD}MineStack${pageIndex + 1}ページ目へ")
          .lore(List(s"$RESET$DARK_RED${UNDERLINE}クリックで移動"))
          .build(),
        action.FilteredButtonEffect(ClickEventFilter.LEFT_CLICK) { _ =>
          sequentialEffect(
            CommonSoundEffects.menuTransitionFenceSound,
            forCategory(category, pageIndex).open
          )
        }
      )

      val previousPageButtonSection =
        if (page > 0)
          Seq(9 * 5 + 7 -> buttonToTransferTo(page - 1, SkullOwners.MHF_ArrowUp))
        else
          Seq()

      val nextPageButtonSection =
        if (page + 1 < totalNumberOfPages)
          Seq(9 * 5 + 8 -> buttonToTransferTo(page + 1, SkullOwners.MHF_ArrowDown))
        else
          Seq()

      mineStackMainMenuButtonSection ++ previousPageButtonSection ++ nextPageButtonSection
    }
  }
}
