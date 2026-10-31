package com.software.sello.designsystem.icon

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.software.sello.designsystem.R

/**
 * Material Symbols Rounded, weight 500, bundled one vector per icon. Icons are filled;
 * the outlined drawing exists only where the reference uses it (unselected tabs).
 */
enum class SelloIcon(@DrawableRes val filled: Int, @DrawableRes val outlined: Int? = null) {
    AccountBalanceWallet(R.drawable.ic_sello_account_balance_wallet),
    Agriculture(R.drawable.ic_sello_agriculture),
    AllInclusive(R.drawable.ic_sello_all_inclusive),
    AccountBalance(
        R.drawable.ic_sello_account_balance,
        R.drawable.ic_sello_account_balance_outlined
    ),
    Apartment(R.drawable.ic_sello_apartment),
    CurrencyBitcoin(R.drawable.ic_sello_currency_bitcoin),
    Flag(R.drawable.ic_sello_flag),
    Close(R.drawable.ic_sello_close),
    PieChart(R.drawable.ic_sello_pie_chart),
    Storefront(R.drawable.ic_sello_storefront),
    TrendingUp(R.drawable.ic_sello_trending_up),
    TrendingDown(R.drawable.ic_sello_trending_down),
    Star(R.drawable.ic_sello_star, R.drawable.ic_sello_star_outline),
    Add(R.drawable.ic_sello_add),
    ArrowBack(R.drawable.ic_sello_arrow_back),
    ArrowUpward(R.drawable.ic_sello_arrow_upward),
    Autorenew(R.drawable.ic_sello_autorenew),
    Backspace(R.drawable.ic_sello_backspace),
    Backup(R.drawable.ic_sello_backup),
    Block(R.drawable.ic_sello_block),
    Bolt(R.drawable.ic_sello_bolt),
    Check(R.drawable.ic_sello_check),
    Checkroom(R.drawable.ic_sello_checkroom),
    ChevronLeft(R.drawable.ic_sello_chevron_left),
    ChevronRight(R.drawable.ic_sello_chevron_right),
    ChildCare(R.drawable.ic_sello_child_care),
    ContentPaste(R.drawable.ic_sello_content_paste),
    Contrast(R.drawable.ic_sello_contrast),
    CreditCard(R.drawable.ic_sello_credit_card),
    CurrencyExchange(R.drawable.ic_sello_currency_exchange),
    Delete(R.drawable.ic_sello_delete),
    DeleteForever(R.drawable.ic_sello_delete_forever),
    DirectionsBus(R.drawable.ic_sello_directions_bus),
    DirectionsCar(R.drawable.ic_sello_directions_car),
    Download(R.drawable.ic_sello_download),
    EditNote(R.drawable.ic_sello_edit_note),
    Error(R.drawable.ic_sello_error),
    Event(R.drawable.ic_sello_event),
    EventBusy(R.drawable.ic_sello_event_busy),
    FitnessCenter(R.drawable.ic_sello_fitness_center),
    Flight(R.drawable.ic_sello_flight),
    Handyman(R.drawable.ic_sello_handyman),
    Home(R.drawable.ic_sello_home),
    Info(R.drawable.ic_sello_info),
    InkPen(R.drawable.ic_sello_ink_pen),
    Insights(R.drawable.ic_sello_insights),
    KeyboardArrowDown(R.drawable.ic_sello_keyboard_arrow_down),
    LocalCafe(R.drawable.ic_sello_local_cafe),
    LocalGasStation(R.drawable.ic_sello_local_gas_station),
    MedicalServices(R.drawable.ic_sello_medical_services),
    MoreVert(R.drawable.ic_sello_more_vert),
    Pause(R.drawable.ic_sello_pause),
    Payments(R.drawable.ic_sello_payments),
    Pets(R.drawable.ic_sello_pets),
    PhoneIphone(R.drawable.ic_sello_phone_iphone),
    PlayArrow(R.drawable.ic_sello_play_arrow),
    ReceiptLong(R.drawable.ic_sello_receipt_long, R.drawable.ic_sello_receipt_long_outlined),
    Redeem(R.drawable.ic_sello_redeem),
    Restaurant(R.drawable.ic_sello_restaurant),
    Savings(R.drawable.ic_sello_savings),
    School(R.drawable.ic_sello_school),
    Sell(R.drawable.ic_sello_sell),
    Settings(R.drawable.ic_sello_settings),
    ShoppingCart(R.drawable.ic_sello_shopping_cart),
    Sort(R.drawable.ic_sello_sort),
    Spa(R.drawable.ic_sello_spa),
    SportsEsports(R.drawable.ic_sello_sports_esports),
    SwapVert(R.drawable.ic_sello_swap_vert),
    Theaters(R.drawable.ic_sello_theaters),
    UploadFile(R.drawable.ic_sello_upload_file),
    Wifi(R.drawable.ic_sello_wifi)
}

@Composable
fun SelloIcon.painter(filled: Boolean = true): Painter =
    painterResource(if (filled) this.filled else outlined ?: this.filled)
