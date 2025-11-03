package lins.applications.mychinesecalender.ui.content

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import lins.applications.mychinesecalender.MainViewModel

@Composable
fun MainContent(
    viewModel: MainViewModel,
    modifier: Modifier
){
    val data = viewModel.lunarDate

    LazyColumn(
        modifier = modifier
    ) {
        items(
            count = data.value.getLength()
        ){
            Text(
                text = data.value.asList()[it] ?: ""
            )
        }

    }

}