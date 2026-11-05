package app.tasbih.counter.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CardShape = RoundedCornerShape(24.dp)
val PillShape = RoundedCornerShape(50)

@Composable
fun TopBar(title: String, trailing: @Composable () -> Unit = {}) {
    Column(Modifier.statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 20.dp),
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            trailing()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    }
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(CardShape).background(colors.card).padding(20.dp), content = content)
}

@Composable
fun Pill(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontWeight = FontWeight.Medium,
        color = if (selected) colors.onAccent else colors.ink,
        modifier = Modifier
            .clip(PillShape)
            .background(if (selected) colors.accent else colors.sand.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}
