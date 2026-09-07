@import io.jimble.util.data.Data
@import java.util.List
@param Data data
!{List<Data> items = data.getDataList("items");}
<ul>
@for(Data item : items)
<li>${item.getString("name")}</li>
@endfor
</ul>
