package ua.savosh.ecoflowdiag.ui

import ua.savosh.ecoflowdiag.core.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StationInfoScreen(vm: AppViewModel) {
    val sn = vm.infoSn ?: "—"
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад")) } },
                title = { FitText(tr("Повна інформація про станцію"), fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SoonBadge()

            SectionCard(tr("Станція")) {
                Text(sn, fontWeight = FontWeight.SemiBold)
                Text(tr("Серійний номер"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard(tr("Навіщо це")) {
                Text(
                    tr("Розділ для перевірки станції перед купівлею (щоб знати, що береш, і що тебе не обманюють): повна «біографія» станції в одному місці."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(tr("Що планується показувати")) {
                Bullet(tr("Модель і покоління, дата виробництва (за серійним номером)"))
                Bullet(tr("Версії прошивок (PD, інвертор, MPPT, Wi-Fi, BMS)"))
                Bullet(tr("Реальний стан батареї: SOH, цикли, розкид комірок"))
                Bullet(tr("Напрацювання і енергія за весь час"))
                Bullet(tr("Перше увімкнення / дата активації — якщо хмара EcoFlow віддає"))
                Bullet(tr("Кількість власників / прив'язок акаунта — якщо хмара EcoFlow віддає"))
            }

            SectionCard(tr("Чесно про обмеження")) {
                Text(
                    tr("Частину (модель, прошивки, цикли, енергію, здоров'я) можна зчитати вже зараз — її підтягну в цей розділ. А от «кількість власників» і «дата першої активації» залежать від того, чи віддає їх закритий API EcoFlow — це ще перевіряю. Що виявиться доступним — з'явиться тут з оновленням."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
